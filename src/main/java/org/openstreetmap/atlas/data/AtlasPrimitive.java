package org.openstreetmap.atlas.data;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import org.openstreetmap.atlas.geography.atlas.items.AtlasEntity;
import org.openstreetmap.josm.data.osm.AbstractPrimitive;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataIntegrityProblemException;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.IPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.User;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.PrimitiveVisitor;
import org.openstreetmap.josm.gui.mappaint.StyleCache;
import org.openstreetmap.josm.tools.Utils;

/**
 * Atlas primitive
 * 
 * @author Vincent Privat
 */
public abstract class AtlasPrimitive implements IPrimitive
{
    static final String ATLAS_ID = "atlas_id";

    /** the parent dataset */
    private AtlasDataSet dataSet;

    private final AtlasEntity item;

    /**
     * Unique identifier in OSM. This is used to identify objects on the server. An id of 0 means an
     * unknown id. The object has not been uploaded yet to know what id it will get.
     */
    private long identifier;

    private Object referrers;

    private StyleCache mappaintStyle;
    private short mappaintCacheIdx;

    private static final AtomicLong idCounter = new AtomicLong(0);

    /**
     * Generates a new primitive unique id.
     * 
     * @return new primitive unique (negative) id
     */
    static long generateUniqueId()
    {
        return idCounter.decrementAndGet();
    }

    protected AtlasPrimitive()
    {
        this(null);
    }

    protected AtlasPrimitive(final AtlasEntity item)
    {
        this.item = item;
        this.identifier = item != null ? item.getIdentifier() : generateUniqueId();
    }

    @Override
    public final void setKeys(final Map<String, String> keys)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public final Map<String, String> getKeys()
    {
        return item != null ? item.getTags() : Collections.emptyMap();
    }

    @Override
    public final void put(final String key, final String value)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public final String get(final String key)
    {
        return item != null ? item.getTag(key).orElse(null) : null;
    }

    @Override
    public final void remove(final String key)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public final boolean hasKeys()
    {
        return item != null ? !item.getTags().isEmpty() : false;
    }

    @Override
    public final Collection<String> keySet()
    {
        return item != null ? item.getTags().keySet() : Collections.emptyList();
    }

    @Override
    public final int getNumKeys()
    {
        return item != null ? item.getTags().size() : 0;
    }

    @Override
    public final void removeAll()
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public final StyleCache getCachedStyle()
    {
        return mappaintStyle;
    }

    @Override
    public final void setCachedStyle(final StyleCache mappaintStyle)
    {
        this.mappaintStyle = mappaintStyle;
    }

    @Override
    public final boolean isCachedStyleUpToDate()
    {
        return mappaintStyle != null && mappaintCacheIdx == dataSet.getMappaintCacheIndex();
    }

    @Override
    public final void declareCachedStyleUpToDate()
    {
        this.mappaintCacheIdx = dataSet.getMappaintCacheIndex();
    }

    @Override
    public final long getUniqueId()
    {
        return identifier;
    }

    @Override
    public long getOsmId()
    {
        return item != null ? item.getOsmIdentifier() : identifier;
    }

    @Override
    public final boolean isNew()
    {
        return false;
    }

    @Override
    public final boolean isModified()
    {
        return false;
    }

    @Override
    public final void setModified(final boolean modified)
    {
        // Do nothing
    }

    @Override
    public final boolean isVisible()
    {
        return true;
    }

    @Override
    public final void setVisible(final boolean visible)
    {
        // Do nothing
    }

    @Override
    public final boolean isDeleted()
    {
        return false;
    }

    @Override
    public final void setDeleted(final boolean deleted)
    {
        // Do nothing
    }

    @Override
    public final boolean isIncomplete()
    {
        return false;
    }

    @Override
    public final boolean isUndeleted()
    {
        return false;
    }

    @Override
    public final boolean isUsable()
    {
        return true;
    }

    @Override
    public final boolean isNewOrUndeleted()
    {
        return false;
    }

    @Override
    public final long getId()
    {
        return identifier >= 0 ? identifier : 0;
    }

    @Override
    public final int getVersion()
    {
        return 1;
    }

    @Override
    public final void setOsmId(final long identifier, final int version)
    {
        if (identifier <= 0)
        {
            throw new IllegalArgumentException(tr("ID > 0 expected. Got {0}.", identifier));
        }
        this.identifier = identifier;
    }

    @Override
    public final User getUser()
    {
        return null;
    }

    @Override
    public final void setUser(final User user)
    {
        // Do nothing
    }

    @Override
    public final Date getTimestamp()
    {
        return null;
    }

    @Override
    public final int getRawTimestamp()
    {
        return 0;
    }

    @Override
    public final void setTimestamp(final Date timestamp)
    {
        // Do nothing
    }

    @Override
    public final void setRawTimestamp(final int timestamp)
    {
        // Do nothing
    }

    @Override
    public final boolean isTimestampEmpty()
    {
        return true;
    }

    @Override
    public final int getChangesetId()
    {
        return 0;
    }

    @Override
    public void setChangesetId(final int changesetId)
    {
        // Do nothing
    }

    @Override
    public void visitReferrers(final PrimitiveVisitor visitor)
    {
        if (visitor != null)
        {
            doVisitReferrers(o -> o.accept(visitor));
        }
    }

    private void doVisitReferrers(final Consumer<AtlasPrimitive> visitor)
    {
        if (referrers == null)
        {
            return;
        }
        else if (referrers instanceof AtlasPrimitive)
        {
            final AtlasPrimitive ref = (AtlasPrimitive) this.referrers;
            if (ref.dataSet == dataSet)
            {
                visitor.accept(ref);
            }
        }
        else if (referrers instanceof AtlasPrimitive[])
        {
            final AtlasPrimitive[] refs = (AtlasPrimitive[]) this.referrers;
            for (final AtlasPrimitive ref : refs)
            {
                if (ref.dataSet == dataSet)
                {
                    visitor.accept(ref);
                }
            }
        }
    }

    /**
     * Return true, if this primitive is a node referred by at least n ways
     * 
     * @param count
     *            Minimal number of ways to return true. Must be positive
     * @return {@code true} if this primitive is referred by at least n ways
     */
    protected final boolean isNodeReferredByWays(final int count)
    {
        // Count only referrers that are members of the same dataset (primitive can have some fake
        // references, for example
        // when way is cloned
        if (referrers == null)
        {
            return false;
        }
        checkDataset();
        if (referrers instanceof AtlasPrimitive)
        {
            return count <= 1 && referrers instanceof Way
                    && ((AtlasPrimitive) referrers).dataSet == dataSet;
        }
        else
        {
            int counter = 0;
            for (final AtlasPrimitive osm : (AtlasPrimitive[]) referrers)
            {
                if (dataSet == osm.dataSet && osm instanceof AtlasLinear && ++counter >= count)
                {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * Add new referrer. If referrer is already included then no action is taken
     * 
     * @param referrer
     *            The referrer to add
     */
    protected void addReferrer(final AtlasPrimitive referrer)
    {
        if (referrers == null)
        {
            referrers = referrer;
        }
        else if (referrers instanceof AtlasPrimitive)
        {
            if (referrers != referrer)
            {
                referrers = new AtlasPrimitive[] { (AtlasPrimitive) referrers, referrer };
            }
        }
        else
        {
            for (final AtlasPrimitive primitive : (AtlasPrimitive[]) referrers)
            {
                if (primitive == referrer)
                {
                    return;
                }
            }
            referrers = Utils.addInArrayCopy((AtlasPrimitive[]) referrers, referrer);
        }
    }

    @Override
    public void setHighlighted(final boolean highlighted)
    {
        // TODO Auto-generated method stub

    }

    @Override
    public boolean isHighlighted()
    {
        // TODO Auto-generated method stub
        return false;
    }

    @Override
    public boolean isTagged()
    {
        // TODO flags
        return item != null ? !item.getOsmTags().isEmpty() : false;
    }

    @Override
    public boolean isAnnotated()
    {
        // TODO flags
        return false;
    }

    @Override
    public boolean hasDirectionKeys()
    {
        // TODO flags
        return false;
    }

    @Override
    public boolean reversedDirection()
    {
        // TODO flags
        return false;
    }

    @Override
    public final List<? extends IPrimitive> getReferrers(final boolean allowWithoutDataset)
    {
        // Returns only referrers that are members of the same dataset (primitive can have some fake
        // references, for example
        // when way is cloned

        if (dataSet == null && allowWithoutDataset)
        {
            return Collections.emptyList();
        }

        checkDataset();
        final Object referrers = this.referrers;
        final List<AtlasPrimitive> result = new ArrayList<>();
        if (referrers != null)
        {
            if (referrers instanceof AtlasPrimitive)
            {
                final AtlasPrimitive ref = (AtlasPrimitive) referrers;
                if (ref.dataSet == dataSet)
                {
                    result.add(ref);
                }
            }
            else
            {
                for (final AtlasPrimitive osm : (AtlasPrimitive[]) referrers)
                {
                    if (dataSet == osm.dataSet)
                    {
                        result.add(osm);
                    }
                }
            }
        }
        return result;
    }

    /**
     * This method should never ever by called from somewhere else than AtlasDataSet.addPrimitive or
     * removePrimitive methods
     * 
     * @param dataSet
     *            the parent dataset
     */
    final void setDataset(final AtlasDataSet dataSet)
    {
        if (this.dataSet != null && dataSet != null && this.dataSet != dataSet)
        {
            throw new DataIntegrityProblemException(
                    "Primitive cannot be included in more than one AtlasDataSet");
        }
        this.dataSet = dataSet;
    }

    @Override
    public final AtlasDataSet getDataSet()
    {
        return dataSet;
    }

    /**
     * Throws exception if primitive is not part of the dataset
     */
    public final void checkDataset()
    {
        if (dataSet == null)
        {
            throw new DataIntegrityProblemException(
                    "Primitive must be part of the dataset: " + toString());
        }
    }

    @Override
    public boolean isSelected()
    {
        return dataSet != null && dataSet.isSelected(this);
    }

    @Override
    public boolean isMemberOfSelected()
    {
        if (referrers == null)
        {
            return false;
        }
        if (referrers instanceof AtlasPrimitive)
        {
            return referrers instanceof AtlasRelation && ((AtlasPrimitive) referrers).isSelected();
        }
        for (final AtlasPrimitive ref : (AtlasPrimitive[]) referrers)
        {
            if (ref instanceof AtlasRelation && ref.isSelected())
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public Map<String, String> getInterestingTags()
    {
        final Map<String, String> result = new HashMap<>();
        if (item != null)
        {
            for (final Entry<String, String> entry : item.getOsmTags().entrySet())
            {
                if (!AbstractPrimitive.isUninterestingKey(entry.getKey()))
                {
                    result.put(entry.getKey(), entry.getValue());
                }
            }
        }
        return result;
    }

    /**
     * Called by AtlasDataset to update cached position information of primitive (bbox, cached
     * EarthNorth, ...)
     */
    public abstract void updatePosition();

    /**
     * If necessary, extend the bbox to contain this primitive
     * 
     * @param box
     *            a bbox instance
     * @param visited
     *            a set of visited members or null
     */
    protected abstract void addToBBox(BBox box, Set<PrimitiveId> visited);

    public abstract OsmPrimitive toOsmPrimitive(DataSet dataSet);

    protected final void addOsmPrimitive(final DataSet dataSet, final OsmPrimitive osm)
    {
        osm.put(ATLAS_ID, Long.toString(getUniqueId()));
        dataSet.addPrimitive(osm);
    }

    protected final String getFlagsAsString()
    {
        final StringBuilder builder = new StringBuilder();

        if (isIncomplete())
        {
            builder.append('I');
        }
        if (isModified())
        {
            builder.append('M');
        }
        if (isVisible())
        {
            builder.append('V');
        }
        if (isDeleted())
        {
            builder.append('D');
        }

        if (isDisabled())
        {
            if (isDisabledAndHidden())
            {
                builder.append('h');
            }
            else
            {
                builder.append('d');
            }
        }
        if (isTagged())
        {
            builder.append('T');
        }
        if (hasDirectionKeys())
        {
            if (reversedDirection())
            {
                builder.append('<');
            }
            else
            {
                builder.append('>');
            }
        }
        return builder.toString();
    }
}
