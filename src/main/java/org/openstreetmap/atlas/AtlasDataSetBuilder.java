package org.openstreetmap.atlas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Supplier;

import org.openstreetmap.atlas.data.AtlasArea;
import org.openstreetmap.atlas.data.AtlasDataSet;
import org.openstreetmap.atlas.data.AtlasEdge;
import org.openstreetmap.atlas.data.AtlasLine;
import org.openstreetmap.atlas.data.AtlasNode;
import org.openstreetmap.atlas.data.AtlasPoint;
import org.openstreetmap.atlas.data.AtlasPrimitive;
import org.openstreetmap.atlas.data.AtlasPunctual;
import org.openstreetmap.atlas.data.AtlasRelation;
import org.openstreetmap.atlas.data.AtlasRelationMember;
import org.openstreetmap.atlas.geography.Location;
import org.openstreetmap.atlas.geography.PolyLine;
import org.openstreetmap.atlas.geography.Polygon;
import org.openstreetmap.atlas.geography.atlas.Atlas;
import org.openstreetmap.atlas.geography.atlas.items.Area;
import org.openstreetmap.atlas.geography.atlas.items.AtlasItem;
import org.openstreetmap.atlas.geography.atlas.items.Edge;
import org.openstreetmap.atlas.geography.atlas.items.Line;
import org.openstreetmap.atlas.geography.atlas.items.LocationItem;
import org.openstreetmap.atlas.geography.atlas.items.Node;
import org.openstreetmap.atlas.geography.atlas.items.Point;
import org.openstreetmap.atlas.geography.atlas.items.Relation;
import org.openstreetmap.atlas.geography.atlas.items.RelationMember;
import org.openstreetmap.atlas.utilities.collections.Iterables;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataIntegrityProblemException;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.tools.Logging;

/**
 * @author jgage
 */
public class AtlasDataSetBuilder
{
    // Version of OsmPrimitive, required when setting OsmId in OpenStreetMap
    private static int IDENTIFIER_VERSION = 1;
    private static Bounds bounds;

    /**
     * Converts atlas objects to their OSM equivalents and stores them in a dataSet. Currently just
     * converts edges, ways, and areas.
     *
     * @param atlas
     *            The atlas to read
     * @param monitor
     *            The progress monitor to monitor loading
     * @return The dataset
     */
    public AtlasDataSet build(final Atlas atlas, final ProgressMonitor monitor)
    {
        final Map<Location, AtlasNode> nodeMap = new HashMap<>();
        final Map<Location, AtlasPoint> pointMap = new HashMap<>();
        final AtlasDataSet dataSet = new AtlasDataSet();
        convertNodes(atlas, monitor, nodeMap, dataSet);
        convertPoints(atlas, monitor, pointMap, dataSet);
        convertEdges(atlas, monitor, nodeMap, dataSet);
        convertLines(atlas, monitor, pointMap, dataSet);
        convertAreas(atlas, monitor, dataSet);
        convertRelations(atlas, monitor, dataSet);
        monitor.setCustomText(
                "Done adding atlas objects to data set. Please wait for layer to build...");
        return dataSet;
    }

    private void convertNodes(final Atlas atlas, final ProgressMonitor monitor,
            final Map<Location, AtlasNode> nodeMap, final AtlasDataSet dataSet)
    {
        monitor.setCustomText("Converting nodes...");
        for (final Node node : atlas.nodes())
        {
            addOsmNode(dataSet, monitor, node.getIdentifier(), node.getLocation(), node.relations(),
                    nodeMap, () -> new AtlasNode(node));
        }
    }

    private void convertPoints(final Atlas atlas, final ProgressMonitor monitor,
            final Map<Location, AtlasPoint> pointMap, final AtlasDataSet dataSet)
    {
        monitor.setCustomText("Converting points...");
        for (final Point point : atlas.points())
        {
            addOsmNode(dataSet, monitor, point.getIdentifier(), point.getLocation(),
                    point.relations(), pointMap, () -> new AtlasPoint(point));
        }
    }

    private void convertEdges(final Atlas atlas, final ProgressMonitor monitor,
            final Map<Location, AtlasNode> nodeMap, final AtlasDataSet dataSet)
    {
        monitor.setCustomText("Converting edges...");
        buildShapePoints(dataSet, monitor,
                Iterables.stream(atlas.edges()).map(Edge::asPolyLine).collect(), nodeMap,
                AtlasNode::new);
        for (final Edge edge : atlas.edges())
        {
            final AtlasEdge way = new AtlasEdge(edge);
            // Create the nodes that come from polyLine shapepoints
            final List<AtlasNode> nodes = new ArrayList<>();
            for (final Location location : edge.asPolyLine())
            {
                final Double nodeLat = location.getLatitude().asDegrees();
                final Double nodeLon = location.getLongitude().asDegrees();
                final LatLon latlon = new LatLon(nodeLat, nodeLon);
                if (bounds == null)
                {
                    bounds = new Bounds(latlon);
                }
                else
                {
                    bounds.extend(latlon);
                }
                nodes.add(nodeMap.get(location));
            }
            way.setNodes(nodes);
            // only takes positive direction because OSM doesn't allow negative ID's and only one
            // direction is required for visualization
            if (edge.getIdentifier() > 0)
            {
                way.setOsmId(edge.getIdentifier(), IDENTIFIER_VERSION);
                dataSet.addPrimitive(way);
            }
        }
    }

    private void convertLines(final Atlas atlas, final ProgressMonitor monitor,
            final Map<Location, AtlasPoint> pointMap, final AtlasDataSet dataSet)
    {
        monitor.setCustomText("Converting lines...");
        buildShapePoints(dataSet, monitor,
                Iterables.stream(atlas.lines()).map(Line::asPolyLine).collect(), pointMap,
                AtlasPoint::new);
        for (final Line line : atlas.lines())
        {
            final AtlasLine way = new AtlasLine(line);
            final List<AtlasPoint> points = new ArrayList<>();
            for (final Location location : line.asPolyLine())
            {
                final Double nodeLat = location.getLatitude().asDegrees();
                final Double nodeLon = location.getLongitude().asDegrees();
                final LatLon latlon = new LatLon(nodeLat, nodeLon);
                if (bounds == null)
                {
                    bounds = new Bounds(latlon);
                }
                else
                {
                    bounds.extend(latlon);
                }
                points.add(pointMap.get(location));
            }
            way.setNodes(points);
            if (line.getIdentifier() > 0)
            {
                way.setOsmId(line.getIdentifier(), IDENTIFIER_VERSION);
            }
            dataSet.addPrimitive(way);
        }
    }

    private void convertAreas(final Atlas atlas, final ProgressMonitor monitor,
            final AtlasDataSet dataSet)
    {
        monitor.setCustomText("Converting areas...");
        for (final Area area : atlas.areas())
        {
            final AtlasArea way = new AtlasArea(area);
            final List<AtlasPoint> points = new ArrayList<>();
            final Polygon polygon = area.asPolygon();
            for (final Location location : polygon)
            {
                final Double nodeLat = location.getLatitude().asDegrees();
                final Double nodeLon = location.getLongitude().asDegrees();
                final LatLon latlon = new LatLon(nodeLat, nodeLon);
                if (bounds == null)
                {
                    bounds = new Bounds(latlon);
                }
                else
                {
                    bounds.extend(latlon);
                }
                final AtlasPoint node = new AtlasPoint(latlon);
                dataSet.addPrimitive(node);
                points.add(node);
            }
            // first node added again so JOSM draws area
            final Location lastlocation = polygon.first();
            final Double lastNodeLat = lastlocation.getLatitude().asDegrees();
            final Double lastNodeLon = lastlocation.getLongitude().asDegrees();
            final LatLon lastLatLon = new LatLon(lastNodeLat, lastNodeLon);
            final AtlasPoint lastNode = new AtlasPoint(lastLatLon);
            dataSet.addPrimitive(lastNode);
            points.add(lastNode);
            way.setNodes(points);
            way.setOsmId(Math.abs(area.getIdentifier()), IDENTIFIER_VERSION);
            dataSet.addPrimitive(way);
        }
    }

    private void convertRelations(final Atlas atlas, final ProgressMonitor monitor,
            final AtlasDataSet dataSet)
    {
        monitor.setCustomText("Converting relations.");
        for (final Relation relation : atlas.relations())
        {
            final AtlasRelation osmRelation = new AtlasRelation(relation);
            final List<AtlasRelationMember> memberList = new ArrayList<>();
            for (final RelationMember member : relation.members())
            {
                final AtlasPrimitive primitive;
                final long identifier = member.getEntity().getIdentifier();
                if (member.getEntity() instanceof LocationItem)
                {
                    // Node & Point
                    primitive = dataSet.getPrimitiveById(identifier, OsmPrimitiveType.NODE);
                }
                else if (member.getEntity() instanceof AtlasItem)
                {
                    // Edge, Area, Line
                    primitive = dataSet.getPrimitiveById(identifier, OsmPrimitiveType.WAY);
                }
                else
                {
                    // Relation
                    primitive = dataSet.getPrimitiveById(identifier, OsmPrimitiveType.RELATION);
                }
                if (primitive != null)
                {
                    memberList.add(new AtlasRelationMember(member, primitive));
                }
            }
            osmRelation.setMembers(memberList);
            final long relationId = relation.getIdentifier();
            monitor.setCustomText("Adding relation " + relationId + " to the data set...");
            osmRelation.setOsmId(relationId, IDENTIFIER_VERSION);
            dataSet.addPrimitive(osmRelation);
        }
    }

    public Bounds getBounds()
    {
        return bounds;
    }

    private <L extends LocationItem, N extends AtlasPunctual> void addOsmNode(
            final AtlasDataSet dataSet, final ProgressMonitor monitor, final long identifier,
            final Location location, final Iterable<Relation> relations,
            final Map<Location, N> nodeMap, final Supplier<N> builder)
    {
        if (!nodeMap.containsKey(location))
        {
            final Double lat = location.getLatitude().asDegrees();
            final Double lon = location.getLongitude().asDegrees();
            final LatLon latlon = new LatLon(lat, lon);
            if (bounds == null)
            {
                bounds = new Bounds(latlon);
            }
            else
            {
                bounds.extend(latlon);
            }
            final N nodeOSM = builder.get();
            if (identifier > 0)
            {
                nodeOSM.setOsmId(identifier, IDENTIFIER_VERSION);
            }
            try
            {
                dataSet.addPrimitive(nodeOSM);
                nodeMap.put(location, nodeOSM);
            }
            catch (final DataIntegrityProblemException e)
            {
                // Node and Points can share the same ID
                Logging.debug(e);
            }
        }
    }

    private <N extends AtlasPunctual> void buildShapePoints(final AtlasDataSet dataSet,
            final ProgressMonitor monitor, final Iterable<PolyLine> polyLines,
            final Map<Location, N> nodeMap, final Function<Location, N> builder)
    {
        for (final PolyLine polyLine : polyLines)
        {
            for (final Location shapePoint : polyLine)
            {
                addOsmNode(dataSet, monitor, -1, shapePoint, new ArrayList<>(), nodeMap,
                        () -> builder.apply(shapePoint));
            }
        }
    }
}
