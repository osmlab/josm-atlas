package org.openstreetmap.atlas.data;

import java.util.Objects;

import org.openstreetmap.atlas.geography.atlas.items.RelationMember;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.IRelationMember;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;

/**
 * Atlas relation member
 * 
 * @author Vincent Privat
 */
public class AtlasRelationMember implements IRelationMember<AtlasPrimitive>
{
    private final RelationMember member;
    private final AtlasPrimitive primitive;

    public AtlasRelationMember(final RelationMember member, final AtlasPrimitive primitive)
    {
        this.member = Objects.requireNonNull(member);
        this.primitive = Objects.requireNonNull(primitive);
    }

    @Override
    public final long getUniqueId()
    {
        return primitive.getUniqueId();
    }

    @Override
    public final OsmPrimitiveType getType()
    {
        return primitive.getType();
    }

    @Override
    public final boolean isNew()
    {
        return false;
    }

    @Override
    public final String getRole()
    {
        return member.getRole();
    }

    @Override
    public final boolean isNode()
    {
        return primitive instanceof AtlasPunctual;
    }

    @Override
    public final boolean isWay()
    {
        return primitive instanceof AtlasLinear;
    }

    @Override
    public final boolean isRelation()
    {
        return primitive instanceof AtlasRelation;
    }

    @Override
    public final AtlasPrimitive getMember()
    {
        return primitive;
    }

    public final org.openstreetmap.josm.data.osm.RelationMember toRelationMember(
            final DataSet dataSet)
    {
        return new org.openstreetmap.josm.data.osm.RelationMember(getRole(),
                getMember().toOsmPrimitive(dataSet));
    }
}
