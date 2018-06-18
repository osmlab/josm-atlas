package org.openstreetmap.atlas.data;

import org.openstreetmap.atlas.geography.Location;
import org.openstreetmap.atlas.geography.atlas.items.Node;
import org.openstreetmap.josm.data.coor.LatLon;

/**
 * Atlas node
 * 
 * @author Vincent Privat
 */
public class AtlasNode extends AtlasPunctual
{
    public AtlasNode(final Node node)
    {
        super(node);
    }

    public AtlasNode(final Location location)
    {
        super(location);
    }

    public AtlasNode(final LatLon latlon)
    {
        super(latlon);
    }

    @Override
    public String toString()
    {
        final String coorDesc = isLatLonKnown() ? "lat=" + lat() + ",lon=" + lon() : "";
        return "{AtlasNode id=" + getUniqueId() + ' ' + getFlagsAsString() + ' ' + coorDesc + '}';
    }
}
