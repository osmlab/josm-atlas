package org.openstreetmap.atlas.data;

import org.openstreetmap.atlas.geography.Location;
import org.openstreetmap.atlas.geography.atlas.items.Point;
import org.openstreetmap.josm.data.coor.LatLon;

/**
 * Atlas point
 * 
 * @author Vincent Privat
 */
public class AtlasPoint extends AtlasPunctual
{
    public AtlasPoint(final Point point)
    {
        super(point);
    }

    public AtlasPoint(final Location location)
    {
        super(location);
    }

    public AtlasPoint(final LatLon latlon)
    {
        super(latlon);
    }

    @Override
    public String toString()
    {
        final String coorDesc = isLatLonKnown() ? "lat=" + lat() + ",lon=" + lon() : "";
        return "{AtlasPoint id=" + getUniqueId() + ' ' + getFlagsAsString() + ' ' + coorDesc + '}';
    }
}
