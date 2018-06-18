package org.openstreetmap.atlas.data;

import java.util.Objects;
import java.util.Set;

import org.openstreetmap.atlas.geography.Latitude;
import org.openstreetmap.atlas.geography.Location;
import org.openstreetmap.atlas.geography.Longitude;
import org.openstreetmap.atlas.geography.atlas.items.LocationItem;
import org.openstreetmap.josm.data.coor.EastNorth;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.INode;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.NodeData;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.visitor.PrimitiveVisitor;

/**
 * Atlas punctual item
 * 
 * @author Vincent Privat
 */
public abstract class AtlasPunctual extends AtlasPrimitive implements INode
{
    private final Location location;

    protected AtlasPunctual(final LocationItem locationItem)
    {
        super(locationItem);
        this.location = locationItem.getLocation();
    }

    protected AtlasPunctual(final Location location)
    {
        this.location = Objects.requireNonNull(location);
    }

    protected AtlasPunctual(final LatLon latlon)
    {
        this(new Location(Latitude.degrees(latlon.lat()), Longitude.degrees(latlon.lon())));
    }

    @Override
    public OsmPrimitiveType getType()
    {
        return OsmPrimitiveType.NODE;
    }

    @Override
    public final void accept(final PrimitiveVisitor visitor)
    {
        visitor.visit(this);
    }

    @Override
    public final BBox getBBox()
    {
        return new BBox(this);
    }

    @Override
    protected void addToBBox(final BBox box, final Set<PrimitiveId> visited)
    {
        box.add(lon(), lat());
    }

    @Override
    public double lon()
    {
        return location.getLongitude().asDegrees();
    }

    @Override
    public double lat()
    {
        return location.getLatitude().asDegrees();
    }

    @Override
    public LatLon getCoor()
    {
        return new LatLon(lat(), lon());
    }

    @Override
    public void setCoor(final LatLon coor)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setEastNorth(final EastNorth eastNorth)
    {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isReferredByWays(final int count)
    {
        return isNodeReferredByWays(count);
    }

    @Override
    public void updatePosition()
    {
        // Do nothing
    }

    @Override
    public Node toOsmPrimitive(final DataSet dataSet)
    {
        Node node = (Node) dataSet.getPrimitiveById(getPrimitiveId());
        if (node == null)
        {
            final NodeData data = new NodeData(getUniqueId());
            data.setCoor(getCoor());
            data.setKeys(getKeys());
            node = new Node(data.getId(), data.getVersion());
            node.setVisible(data.isVisible());
            node.load(data);
            addOsmPrimitive(dataSet, node);
        }
        return node;
    }
}
