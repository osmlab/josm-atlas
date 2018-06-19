package org.openstreetmap.atlas;

import java.util.HashSet;
import java.util.Map;

import javax.swing.DefaultListModel;

import org.apache.commons.lang3.math.NumberUtils;
import org.openstreetmap.atlas.AtlasReaderDialog.PrintablePrimitive;
import org.openstreetmap.atlas.data.AtlasDataSet;
import org.openstreetmap.atlas.data.AtlasLinear;
import org.openstreetmap.atlas.data.AtlasPrimitive;
import org.openstreetmap.atlas.data.AtlasPunctual;
import org.openstreetmap.atlas.data.AtlasRelation;
import org.openstreetmap.atlas.exception.CoreException;
import org.openstreetmap.atlas.geography.atlas.Atlas;
import org.openstreetmap.atlas.utilities.collections.StringList;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.tools.Logging;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/**
 * @author jgage
 */
public class AtlasSearch
{
    /**
     * @author jgage
     */
    public enum SearchType
    {
        OSM_IDENTIFIER("OSM ID"),
        ATLAS_IDENTIFIER("Atlas ID"),
        TAG("Tag"),
        BOX("Box"),
        ALL("All");

        private final String name;

        public static SearchType forName(final String name)
        {
            for (final SearchType candidate : SearchType.values())
            {
                if (candidate.getName().equals(name))
                {
                    return candidate;
                }
            }
            throw new CoreException("Unrecognized search type {}", name);
        }

        SearchType(final String name)
        {
            this.name = name;
        }

        public String getName()
        {
            return this.name;
        }
    }

    private final AtlasDataSet dataSet;
    private final SearchType mode;
    private BiMap<Integer, PrimitiveId> indexToIdentifier;
    private final DefaultListModel<PrintablePrimitive> listAll;
    private final Atlas atlas;
    private final BiMap<Integer, PrimitiveId> indexToIdentifierAll;

    public AtlasSearch(final Atlas atlas, final AtlasDataSet data, final SearchType mode,
            final DefaultListModel<PrintablePrimitive> listAll,
            final BiMap<Integer, PrimitiveId> indexToIdentifierAll)
    {
        this.dataSet = data;
        this.mode = mode;
        this.indexToIdentifier = HashBiMap.create();
        this.listAll = listAll;
        this.indexToIdentifierAll = indexToIdentifierAll;
        this.atlas = atlas;
    }

    public BiMap<Integer, PrimitiveId> getIndexToIdentifier()
    {
        return this.indexToIdentifier;
    }

    public DefaultListModel<PrintablePrimitive> search(final String searchText)
    {
        DefaultListModel<PrintablePrimitive> results = new DefaultListModel<>();
        switch (this.mode)
        {
            case ATLAS_IDENTIFIER:
                if (NumberUtils.isNumber(searchText))
                {
                    try
                    {
                        results = search(Long.parseLong(searchText));
                    }
                    catch (final Exception e)
                    {
                        Logging.error(e);
                    }
                }
                break;
            case OSM_IDENTIFIER:
                return searchOSM(searchText);
            case TAG:
                return searchByTag(searchText);
            case BOX:
                return searchByBoundingBox(searchText);
            case ALL:
                return returnAll();
            default:
                throw new CoreException("Invalid mode {}", this.mode);
        }
        return results;
    }

    /**
     * Search by two tags separated by " AND ".
     */
    private DefaultListModel<PrintablePrimitive> compoundSearch(final String tag)
    {
        final DefaultListModel<PrintablePrimitive> results = new DefaultListModel<>();
        final StringList searchTags = StringList.split(tag, " AND ");
        final DefaultListModel<PrintablePrimitive> firstResults = searchByTag(searchTags.get(0));
        final DefaultListModel<PrintablePrimitive> secondResults = searchByTag(searchTags.get(1));
        final HashSet<AtlasPrimitive> firstPrimitives = new HashSet<>();
        final HashSet<AtlasPrimitive> secondPrimitives = new HashSet<>();
        for (int i = 0; i < firstResults.size(); i++)
        {
            firstPrimitives.add(firstResults.get(i).getOsmPrimitive());
        }
        for (int i = 0; i < secondResults.size(); i++)
        {
            secondPrimitives.add(secondResults.get(i).getOsmPrimitive());
        }
        int index = 0;
        this.indexToIdentifier.clear();
        for (final AtlasPrimitive result : firstPrimitives)
        {
            if (secondPrimitives.contains(result))
            {
                results.addElement(new PrintablePrimitive(index, result));
                this.indexToIdentifier.put(index, result.getPrimitiveId());
                index++;
            }
        }
        return results;
    }

    /**
     * Returns all items in the DataSet.
     */
    private DefaultListModel<PrintablePrimitive> returnAll()
    {
        this.indexToIdentifier = this.indexToIdentifierAll;
        return this.listAll;
    }

    /**
     * Search by Atlas ID.
     */
    private DefaultListModel<PrintablePrimitive> search(final long osmID)
    {
        final DefaultListModel<PrintablePrimitive> results = new DefaultListModel<>();
        this.indexToIdentifier.clear();
        final int index = 0;
        if (this.dataSet.getPrimitiveById(osmID, OsmPrimitiveType.NODE) != null)
        {
            final AtlasPrimitive node = this.dataSet.getPrimitiveById(osmID, OsmPrimitiveType.NODE);
            results.addElement(new PrintablePrimitive(index, node));
            this.indexToIdentifier.put(index, node.getPrimitiveId());
        }
        else if (this.dataSet.getPrimitiveById(osmID, OsmPrimitiveType.WAY) != null)
        {
            final AtlasPrimitive way = this.dataSet.getPrimitiveById(osmID, OsmPrimitiveType.WAY);
            results.addElement(new PrintablePrimitive(index, way));
            this.indexToIdentifier.put(index, way.getPrimitiveId());
        }
        else if (this.dataSet.getPrimitiveById(osmID, OsmPrimitiveType.RELATION) != null)
        {
            final AtlasPrimitive relation = this.dataSet.getPrimitiveById(osmID,
                    OsmPrimitiveType.RELATION);
            results.addElement(new PrintablePrimitive(index, relation));
            this.indexToIdentifier.put(index, relation.getPrimitiveId());
        }
        return results;
    }

    /**
     * Search by bounding box.
     */
    private DefaultListModel<PrintablePrimitive> searchByBoundingBox(final String bounds)
    {
        final DefaultListModel<PrintablePrimitive> results = new DefaultListModel<>();
        if (this.indexToIdentifier != null)
        {
            this.indexToIdentifier.clear();
        }
        int index = 0;
        try
        {
            final String[] splitBounds = bounds.split(":|\\,");
            final int length = 4;
            final double[] coordinates = new double[length];
            for (int i = 0; i < length; i++)
            {
                coordinates[i] = Double.parseDouble(splitBounds[i]);
            }
            final Bounds boundingBox = new Bounds(coordinates);
            for (final AtlasPunctual node : this.dataSet.getNodes())
            {
                // dataSet creates garbage id's for shapepoints, need to filter those nodes
                if (boundingBox.contains(node.getCoor())
                        && (this.atlas.node(node.getPrimitiveId().getUniqueId()) != null
                                || this.atlas.point(node.getPrimitiveId().getUniqueId()) != null))
                {
                    results.addElement(new PrintablePrimitive(index, node));
                    this.indexToIdentifier.put(index, node.getPrimitiveId());
                    index++;
                }
            }
            for (final AtlasLinear<?> way : this.dataSet.getWays())
            {
                if (boundingBox.contains(way.getBBox().getCenter()))
                {
                    results.addElement(new PrintablePrimitive(index, way));
                    this.indexToIdentifier.put(index, way.getPrimitiveId());
                    index++;
                }
            }
            for (final AtlasRelation relation : this.dataSet.getRelations())
            {
                if (boundingBox.contains(relation.getBBox().getCenter()))
                {
                    results.addElement(new PrintablePrimitive(index, relation));
                    this.indexToIdentifier.put(index, relation.getPrimitiveId());
                    index++;
                }
            }
        }
        catch (final Exception e)
        {
            Logging.error(e);
        }
        return results;
    }

    /**
     * Search by tag.
     */
    private DefaultListModel<PrintablePrimitive> searchByTag(final String tag)
    {
        final DefaultListModel<PrintablePrimitive> results = new DefaultListModel<>();
        if (this.indexToIdentifier != null)
        {
            this.indexToIdentifier.clear();
        }
        int index = 0;
        if (tag.contains(" AND "))
        {
            return compoundSearch(tag);
        }
        if (tag.contains("="))
        {
            final StringList keyVal = StringList.split(tag, "=");
            String key = "";
            String val = "";
            if (keyVal.size() > 0)
            {
                key = keyVal.get(0);
            }
            if (keyVal.size() > 1)
            {
                val = keyVal.get(1);
            }
            for (final AtlasPunctual node : this.dataSet.getNodes())
            {
                final Map<String, String> keyMap = node.getKeys();
                if (keyMap.containsValue(val) && keyMap.containsKey(key))
                {
                    results.addElement(new PrintablePrimitive(index, node));
                    this.indexToIdentifier.put(index, node.getPrimitiveId());
                    index++;
                }
            }
            for (final AtlasLinear<?> way : this.dataSet.getWays())
            {
                final Map<String, String> keyMap = way.getKeys();
                if (keyMap.containsValue(val) && keyMap.containsKey(key))
                {
                    results.addElement(new PrintablePrimitive(index, way));
                    this.indexToIdentifier.put(index, way.getPrimitiveId());
                    index++;
                }
            }
            for (final AtlasRelation relation : this.dataSet.getRelations())
            {
                final Map<String, String> keyMap = relation.getKeys();
                if (keyMap.containsValue(val) && keyMap.containsKey(key))
                {
                    results.addElement(new PrintablePrimitive(index, relation));
                    this.indexToIdentifier.put(index, relation.getPrimitiveId());
                    index++;
                }
            }
        }
        else
        {
            for (final AtlasPunctual node : this.dataSet.getNodes())
            {
                final Map<String, String> keyMap = node.getKeys();
                if (keyMap.containsValue(tag) || keyMap.containsKey(tag))
                {
                    results.addElement(new PrintablePrimitive(index, node));
                    this.indexToIdentifier.put(index, node.getPrimitiveId());
                    index++;
                }
            }
            for (final AtlasLinear<?> way : this.dataSet.getWays())
            {
                final Map<String, String> keyMap = way.getKeys();
                if (keyMap.containsValue(tag) || keyMap.containsKey(tag))
                {
                    results.addElement(new PrintablePrimitive(index, way));
                    this.indexToIdentifier.put(index, way.getPrimitiveId());
                    index++;
                }
            }
            for (final AtlasRelation relation : this.dataSet.getRelations())
            {
                final Map<String, String> keyMap = relation.getKeys();
                if (keyMap.containsValue(tag) || keyMap.containsKey(tag))
                {
                    results.addElement(new PrintablePrimitive(index, relation));
                    this.indexToIdentifier.put(index, relation.getPrimitiveId());
                    index++;
                }
            }
        }
        return results;
    }

    /**
     * Search by OSM ID.
     */
    private DefaultListModel<PrintablePrimitive> searchOSM(final String searchText)
    {
        final DefaultListModel<PrintablePrimitive> results = new DefaultListModel<>();
        this.indexToIdentifier.clear();
        int index = 0;
        for (final AtlasPrimitive primitive : this.dataSet.allPrimitives())
        {
            if (String.valueOf(primitive.getPrimitiveId()).contains(searchText)
                    && primitive.getId() > 0)
            {
                results.addElement(new PrintablePrimitive(index, primitive));
                this.indexToIdentifier.put(index, primitive.getPrimitiveId());
                index++;
            }
        }
        return results;
    }
}
