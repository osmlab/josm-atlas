package org.openstreetmap.atlas.data;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.openstreetmap.atlas.geography.atlas.items.Relation;
import org.openstreetmap.josm.data.osm.BBox;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.IRelation;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.RelationData;
import org.openstreetmap.josm.data.osm.visitor.PrimitiveVisitor;

/**
 * Atlas relation
 * 
 * @author Vincent Privat
 */
public class AtlasRelation extends AtlasPrimitive implements IRelation<AtlasRelationMember>
{
    private List<AtlasRelationMember> members;
    private BBox bbox;

    public AtlasRelation(final Relation relation)
    {
        super(relation);
    }

    @Override
    public OsmPrimitiveType getType()
    {
        return OsmPrimitiveType.RELATION;
    }

    @Override
    public final void accept(final PrimitiveVisitor visitor)
    {
        visitor.visit(this);
    }

    @Override
    public final BBox getBBox()
    {
        if (bbox != null)
        {
            return new BBox(bbox);
        }

        final BBox box = new BBox();
        addToBBox(box, new HashSet<PrimitiveId>());
        if (getDataSet() != null)
        {
            setBBox(box);
        }
        return new BBox(box);
    }

    @Override
    public final int getMembersCount()
    {
        return members.size();
    }

    @Override
    public final AtlasRelationMember getMember(final int index)
    {
        return members.get(index);
    }

    @Override
    public final List<AtlasRelationMember> getMembers()
    {
        return members;
    }

    @Override
    public final void setMembers(final List<AtlasRelationMember> members)
    {
        this.members = members;
        for (final AtlasRelationMember relMember : this.members)
        {
            relMember.getMember().addReferrer(this);
            relMember.getMember().clearCachedStyle();
        }
    }

    @Override
    public final long getMemberId(final int idx)
    {
        return members.get(idx).getUniqueId();
    }

    @Override
    public final String getRole(final int idx)
    {
        return members.get(idx).getRole();
    }

    @Override
    public final OsmPrimitiveType getMemberType(final int idx)
    {
        return members.get(idx).getType();
    }

    private void setBBox(final BBox bbox)
    {
        this.bbox = bbox;
    }

    @Override
    protected void addToBBox(final BBox box, final Set<PrimitiveId> visited)
    {
        for (final AtlasRelationMember relMember : members)
        {
            if (visited.add(relMember.getMember()))
            {
                relMember.getMember().addToBBox(box, visited);
            }
        }
    }

    @Override
    public final void updatePosition()
    {
        // make sure that it is recalculated
        setBBox(null);
        setBBox(getBBox());
    }

    @Override
    public org.openstreetmap.josm.data.osm.Relation toOsmPrimitive(final DataSet dataSet)
    {
        org.openstreetmap.josm.data.osm.Relation rel = (org.openstreetmap.josm.data.osm.Relation) dataSet
                .getPrimitiveById(getPrimitiveId());
        if (rel == null)
        {
            final RelationData data = new RelationData(getUniqueId());
            data.setKeys(getKeys());
            rel = new org.openstreetmap.josm.data.osm.Relation(data.getId(), data.getVersion());
            rel.setVisible(data.isVisible());
            rel.load(data);
            rel.setMembers(getMembers().stream().map(m -> m.toRelationMember(dataSet))
                    .collect(Collectors.toList()));
            addOsmPrimitive(dataSet, rel);
        }
        return rel;
    }
}
