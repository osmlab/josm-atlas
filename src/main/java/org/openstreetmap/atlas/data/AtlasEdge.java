package org.openstreetmap.atlas.data;

import org.openstreetmap.atlas.geography.atlas.items.Edge;

/**
 * Atlas edge
 * 
 * @author Vincent Privat
 */
public class AtlasEdge extends AtlasLinear<AtlasNode>
{
    public AtlasEdge(final Edge item)
    {
        super(item);
    }
}
