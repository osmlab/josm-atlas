package org.openstreetmap.atlas.data;

import org.openstreetmap.atlas.geography.atlas.items.Area;

/**
 * Atlas area
 * 
 * @author Vincent Privat
 */
public class AtlasArea extends AtlasLinear<AtlasPoint>
{
    public AtlasArea(final Area item)
    {
        super(item);
    }
}
