package org.openstreetmap.atlas.data;

import org.openstreetmap.atlas.geography.atlas.items.Line;

/**
 * Atlas line
 * 
 * @author Vincent Privat
 */
public class AtlasLine extends AtlasLinear<AtlasPoint>
{
    public AtlasLine(final Line item)
    {
        super(item);
    }
}
