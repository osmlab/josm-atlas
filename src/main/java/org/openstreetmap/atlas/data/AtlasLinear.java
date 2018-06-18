package org.openstreetmap.atlas.data;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.openstreetmap.atlas.geography.atlas.items.Area;
import org.openstreetmap.atlas.geography.atlas.items.LineItem;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.INode;
import org.openstreetmap.josm.data.osm.IWay;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.WayData;
import org.openstreetmap.josm.data.osm.visitor.PrimitiveVisitor;

/**
 * Atlas linear item
 * 
 * @param <P>
 *            type of punctual feature
 * @author Vincent Privat
 */
public abstract class AtlasLinear<P extends AtlasPunctual> extends AtlasPrimitive implements IWay<P>
{
    private static final int THREE = 3;

    /**
     * All way nodes in this way
     */
    private List<P> nodes;
    private BBox bbox;

    protected AtlasLinear(final LineItem item)
    {
        super(item);
    }

    protected AtlasLinear(final Area item)
    {
        super(item);
    }

    @Override
    public OsmPrimitiveType getType()
    {
        return OsmPrimitiveType.WAY;
    }

    @Override
    public final void accept(final PrimitiveVisitor visitor)
    {
        visitor.visit(this);
    }

    @Override
    public final BBox getBBox()
    {
        if (bbox == null)
        {
            bbox = new BBox(this);
        }
        return new BBox(bbox);
    }

    @Override
    public final int getNodesCount()
    {
        return nodes.size();
    }

    @Override
    public final P getNode(final int index)
    {
        return nodes.get(index);
    }

    @Override
    public final List<P> getNodes()
    {
        return nodes;
    }

    @Override
    public final List<Long> getNodeIds()
    {
        return nodes.stream().map(AtlasPunctual::getId).collect(Collectors.toList());
    }

    @Override
    public final long getNodeId(final int idx)
    {
        return nodes.get(idx).getUniqueId();
    }

    @Override
    public final void setNodes(final List<P> nodes)
    {
        this.nodes = nodes;
        for (final P node : this.nodes)
        {
            node.addReferrer(this);
            node.clearCachedStyle();
        }

        clearCachedStyle();
    }

    @Override
    public boolean isClosed()
    {
        final int size = nodes.size();
        return size >= THREE && nodes.get(size - 1) == nodes.get(0);
    }

    @Override
    public void updatePosition()
    {
        bbox = new BBox(this);
        clearCachedStyle();
    }

    @Override
    protected void addToBBox(final BBox box, final Set<PrimitiveId> visited)
    {
        box.add(getBBox());
    }

    @Override
    public final P firstNode()
    {
        if (nodes.isEmpty())
        {
            return null;
        }
        return nodes.get(0);
    }

    @Override
    public final P lastNode()
    {
        if (nodes.isEmpty())
        {
            return null;
        }
        return nodes.get(nodes.size() - 1);
    }

    @Override
    public final boolean isFirstLastNode(final INode node)
    {
        if (nodes.isEmpty())
        {
            return false;
        }
        return node == nodes.get(0) || node == nodes.get(nodes.size() - 1);
    }

    @Override
    public final boolean isInnerNode(final INode node)
    {
        if (nodes.size() <= 2)
        {
            return false;
        }
        // circular ways have only inner nodes, so return true for them!
        if (node == nodes.get(0) && node == nodes.get(nodes.size() - 1))
        {
            return true;
        }
        for (int i = 1; i < nodes.size() - 1; ++i)
        {
            if (nodes.get(i) == node)
            {
                return true;
            }
        }
        return false;
    }

    @Override
    public Way toOsmPrimitive(final DataSet dataSet)
    {
        Way way = (Way) dataSet.getPrimitiveById(getPrimitiveId());
        if (way == null)
        {
            final WayData data = new WayData(getUniqueId());
            data.setKeys(getKeys());
            way = new Way(data.getId(), data.getVersion());
            way.setVisible(data.isVisible());
            way.load(data);
            way.setNodes(getNodes().stream().map(n -> n.toOsmPrimitive(dataSet))
                    .collect(Collectors.toList()));
            addOsmPrimitive(dataSet, way);
        }
        return way;
    }
}
