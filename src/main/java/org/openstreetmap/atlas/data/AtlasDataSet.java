package org.openstreetmap.atlas.data;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.Collection;
import java.util.Collections;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.openstreetmap.josm.data.DataSource;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataIntegrityProblemException;
import org.openstreetmap.josm.data.osm.DataSelectionListener;
import org.openstreetmap.josm.data.osm.DataSelectionListener.SelectionAddEvent;
import org.openstreetmap.josm.data.osm.DataSelectionListener.SelectionChangeEvent;
import org.openstreetmap.josm.data.osm.DataSelectionListener.SelectionRemoveEvent;
import org.openstreetmap.josm.data.osm.DataSelectionListener.SelectionReplaceEvent;
import org.openstreetmap.josm.data.osm.DataSelectionListener.SelectionToggleEvent;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.DownloadPolicy;
import org.openstreetmap.josm.data.osm.HighlightUpdateListener;
import org.openstreetmap.josm.data.osm.OsmData;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.QuadBucketPrimitiveStore;
import org.openstreetmap.josm.data.osm.SimplePrimitiveId;
import org.openstreetmap.josm.data.osm.Storage;
import org.openstreetmap.josm.data.osm.UploadPolicy;
import org.openstreetmap.josm.data.osm.WaySegment;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ListenerList;
import org.openstreetmap.josm.tools.Logging;
import org.openstreetmap.josm.tools.SubclassFilteredCollection;

/**
 * Atlas data set
 *
 * @author Vincent Privat
 */
public final class AtlasDataSet
        implements OsmData<AtlasPrimitive, AtlasPunctual, AtlasLinear<AtlasPunctual>, AtlasRelation>
{
    private final ReadWriteLock lock = new ReentrantReadWriteLock();
    private final QuadBucketPrimitiveStore<AtlasPunctual, AtlasLinear<AtlasPunctual>, AtlasRelation> store = new QuadBucketPrimitiveStore<>();
    private final Storage<AtlasPrimitive> allPrimitives = new Storage<>(
            new Storage.PrimitiveIdHash(), true);
    private final Map<PrimitiveId, AtlasPrimitive> primitivesMap = allPrimitives
            .foreignKey(new Storage.PrimitiveIdHash());

    // provide means to highlight map elements that are not osm primitives
    private Collection<WaySegment> highlightedVirtualNodes = new LinkedList<>();
    private Collection<WaySegment> highlightedWaySegments = new LinkedList<>();

    private short mappaintCacheIdx = 1;

    /**
     * The mutex lock that is used to synchronize selection changes.
     */
    private final Object selectionLock = new Object();

    /**
     * The current selected primitives. This is always a unmodifiable set. The set should be ordered
     * in the order in which the primitives have been added to the selection.
     */
    private Set<AtlasPrimitive> currentSelectedPrimitives = Collections.emptySet();

    /**
     * A list of listeners that listen to selection changes on this layer.
     */
    private final ListenerList<DataSelectionListener> selectionListeners = ListenerList.create();

    /** Fake dataset for selection events */
    private final DataSet selectionDs = new DataSet();

    public AtlasDataSet()
    {
        selectionDs.setUploadPolicy(UploadPolicy.BLOCKED);
        selectionDs.setDownloadPolicy(DownloadPolicy.BLOCKED);
        selectionDs.lock();
    }

    @Override
    public Collection<DataSource> getDataSources()
    {
        return Collections.emptyList();
    }

    @Override
    public void lock()
    {
        // Do nothing
    }

    @Override
    public void unlock()
    {
        // Do nothing
    }

    @Override
    public boolean isLocked()
    {
        return true;
    }

    @Override
    public String getVersion()
    {
        return null;
    }

    @Override
    public String getName()
    {
        return null;
    }

    @Override
    public void setName(final String name)
    {
        // Do nothing
    }

    @Override
    public void addPrimitive(final AtlasPrimitive primitive)
    {
        if (getPrimitiveById(primitive) != null)
        {
            throw new DataIntegrityProblemException(
                    tr("Unable to add primitive {0} to the dataset because it is already included",
                            primitive.toString()));
        }

        allPrimitives.add(primitive);
        primitive.setDataset(this);
        // Set cached bbox for way and relation (required for reindexWay and reindexRelation to work
        // properly)
        primitive.updatePosition();
        store.addPrimitive(primitive);
    }

    @Override
    public void clear()
    {
        clearSelection();
        for (final AtlasPrimitive primitive : allPrimitives)
        {
            primitive.setDataset(null);
        }
        store.clear();
        allPrimitives.clear();
    }

    @Override
    public List<AtlasPunctual> searchNodes(final BBox bbox)
    {
        return store.searchNodes(bbox);
    }

    @Override
    public boolean containsNode(final AtlasPunctual node)
    {
        return store.containsNode(node);
    }

    @Override
    public List<AtlasLinear<AtlasPunctual>> searchWays(final BBox bbox)
    {
        return store.searchWays(bbox);
    }

    @Override
    public boolean containsWay(final AtlasLinear<AtlasPunctual> way)
    {
        return store.containsWay(way);
    }

    @Override
    public List<AtlasRelation> searchRelations(final BBox bbox)
    {
        return store.searchRelations(bbox);
    }

    @Override
    public boolean containsRelation(final AtlasRelation rel)
    {
        return store.containsRelation(rel);
    }

    @Override
    public AtlasPrimitive getPrimitiveById(final PrimitiveId primitiveId)
    {
        return primitiveId != null ? primitivesMap.get(primitiveId) : null;
    }

    /**
     * Show message and stack trace in log in case primitive is not found
     *
     * @param primitiveId
     *            primitive id to look for
     * @return Primitive by id.
     */
    private AtlasPrimitive getPrimitiveByIdChecked(final PrimitiveId primitiveId)
    {
        final AtlasPrimitive result = getPrimitiveById(primitiveId);
        if (result == null && primitiveId != null)
        {
            Logging.warn(tr(
                    "JOSM expected to find primitive [{0} {1}] in dataset but it is not there. Please report this "
                            + "at {2}. This is not a critical error, it should be safe to continue in your work.",
                    primitiveId.getType(), Long.toString(primitiveId.getUniqueId()),
                    Config.getUrls().getJOSMWebsite()));
            Logging.error(new Exception());
        }

        return result;
    }

    @Override
    public <T extends AtlasPrimitive> Collection<T> getPrimitives(
            final Predicate<? super AtlasPrimitive> predicate)
    {
        return new SubclassFilteredCollection<>(allPrimitives, predicate);
    }

    @Override
    public Collection<AtlasPunctual> getNodes()
    {
        return getPrimitives(AtlasPunctual.class::isInstance);
    }

    @Override
    public Collection<AtlasLinear<AtlasPunctual>> getWays()
    {
        return getPrimitives(AtlasLinear.class::isInstance);
    }

    @Override
    public Collection<AtlasRelation> getRelations()
    {
        return getPrimitives(AtlasRelation.class::isInstance);
    }

    @Override
    public DownloadPolicy getDownloadPolicy()
    {
        return DownloadPolicy.BLOCKED;
    }

    @Override
    public void setDownloadPolicy(final DownloadPolicy downloadPolicy)
    {
        // Do nothing
    }

    @Override
    public UploadPolicy getUploadPolicy()
    {
        return UploadPolicy.BLOCKED;
    }

    @Override
    public void setUploadPolicy(final UploadPolicy uploadPolicy)
    {
        // Do nothing
    }

    @Override
    public Lock getReadLock()
    {
        return lock.readLock();
    }

    @Override
    public Collection<WaySegment> getHighlightedVirtualNodes()
    {
        return Collections.unmodifiableCollection(highlightedVirtualNodes);
    }

    @Override
    public Collection<WaySegment> getHighlightedWaySegments()
    {
        return Collections.unmodifiableCollection(highlightedWaySegments);
    }

    @Override
    public void setHighlightedVirtualNodes(final Collection<WaySegment> waySegments)
    {
        if (highlightedVirtualNodes.isEmpty() && waySegments.isEmpty())
        {
            return;
        }

        highlightedVirtualNodes = waySegments;
    }

    @Override
    public void setHighlightedWaySegments(final Collection<WaySegment> waySegments)
    {
        if (highlightedWaySegments.isEmpty() && waySegments.isEmpty())
        {
            return;
        }

        highlightedWaySegments = waySegments;
    }

    @Override
    public void addHighlightUpdateListener(final HighlightUpdateListener listener)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public void removeHighlightUpdateListener(final HighlightUpdateListener listener)
    {
        // TODO Auto-generated method stub
    }

    @Override
    public Collection<AtlasPrimitive> getAllSelected()
    {
        return currentSelectedPrimitives;
    }

    @Override
    public boolean selectionEmpty()
    {
        return currentSelectedPrimitives.isEmpty();
    }

    @Override
    public boolean isSelected(final AtlasPrimitive osm)
    {
        return currentSelectedPrimitives.contains(osm);
    }

    @Override
    public void toggleSelected(final Collection<? extends PrimitiveId> osm)
    {
        toggleSelected(osm.stream());
    }

    @Override
    public void toggleSelected(final PrimitiveId... osm)
    {
        toggleSelected(Stream.of(osm));
    }

    private void toggleSelected(final Stream<? extends PrimitiveId> stream)
    {
        doSelectionChange(old -> new SelectionToggleEvent(selectionDs, toOsmPrimitives(old),
                toOsmPrimitives(stream)));
    }

    private Set<OsmPrimitive> toOsmPrimitives(final Set<AtlasPrimitive> set)
    {
        return set.stream().map(p -> p.toOsmPrimitive(selectionDs)).collect(Collectors.toSet());
    }

    private Stream<OsmPrimitive> toOsmPrimitives(final Stream<? extends PrimitiveId> stream)
    {
        return stream.map(this::getPrimitiveByIdChecked).filter(Objects::nonNull)
                .map(p -> p.toOsmPrimitive(selectionDs));
    }

    private AtlasPrimitive toAtlasPrimitive(final OsmPrimitive osm)
    {
        return getPrimitiveByIdChecked(new SimplePrimitiveId(
                Long.parseLong(osm.get(AtlasPrimitive.ATLAS_ID)), osm.getType()));
    }

    private Set<AtlasPrimitive> toAtlasPrimitives(final Set<OsmPrimitive> set)
    {
        return set.stream().map(this::toAtlasPrimitive).collect(Collectors.toSet());
    }

    /**
     * Do a selection change.
     * <p>
     * This is the only method that changes the current selection state.
     *
     * @param command
     *            A generator that generates the {@link SelectionChangeEvent} for the given base set
     *            of currently selected primitives.
     * @return true iff the command did change the selection.
     */
    private boolean doSelectionChange(
            final Function<Set<AtlasPrimitive>, SelectionChangeEvent> command)
    {
        synchronized (selectionLock)
        {
            selectionDs.unlock();
            selectionDs.clear();
            final SelectionChangeEvent event = command.apply(currentSelectedPrimitives);
            selectionDs.lock();
            if (event.isNop())
            {
                return false;
            }
            currentSelectedPrimitives = toAtlasPrimitives(event.getSelection());
            selectionListeners.fireEvent(l -> l.selectionChanged(event));
            return true;
        }
    }

    @Override
    public void setSelected(final Collection<? extends PrimitiveId> selection)
    {
        setSelected(selection.stream());
    }

    @Override
    public void setSelected(final PrimitiveId... osm)
    {
        setSelected(Stream.of(osm).filter(Objects::nonNull));
    }

    private void setSelected(final Stream<? extends PrimitiveId> stream)
    {
        doSelectionChange(old -> new SelectionReplaceEvent(selectionDs, toOsmPrimitives(old),
                toOsmPrimitives(stream)));
    }

    @Override
    public void addSelected(final Collection<? extends PrimitiveId> selection)
    {
        addSelected(selection.stream());
    }

    @Override
    public void addSelected(final PrimitiveId... osm)
    {
        addSelected(Stream.of(osm));
    }

    private void addSelected(final Stream<? extends PrimitiveId> stream)
    {
        doSelectionChange(old -> new SelectionAddEvent(selectionDs, toOsmPrimitives(old),
                toOsmPrimitives(stream)));
    }

    @Override
    public void clearSelection(final PrimitiveId... osm)
    {
        clearSelection(Stream.of(osm));
    }

    @Override
    public void clearSelection(final Collection<? extends PrimitiveId> list)
    {
        clearSelection(list.stream());
    }

    private void clearSelection(final Stream<? extends PrimitiveId> stream)
    {
        doSelectionChange(old -> new SelectionRemoveEvent(selectionDs, toOsmPrimitives(old),
                toOsmPrimitives(stream)));
    }

    @Override
    public void clearSelection()
    {
        setSelected(Stream.empty());
    }

    @Override
    public void addSelectionListener(final DataSelectionListener listener)
    {
        selectionListeners.addListener(listener);
    }

    @Override
    public void removeSelectionListener(final DataSelectionListener listener)
    {
        selectionListeners.removeListener(listener);
    }

    /**
     * Returns mappaint cache index for this DataSet. If the {@link OsmPrimitive#mappaintCacheIdx}
     * is not equal to the DataSet mappaint cache index, this means the cache for that primitive is
     * out of date.
     *
     * @return mappaint cache index
     */
    public short getMappaintCacheIndex()
    {
        return mappaintCacheIdx;
    }

    @Override
    public void clearMappaintCache()
    {
        mappaintCacheIdx++;
    }
}
