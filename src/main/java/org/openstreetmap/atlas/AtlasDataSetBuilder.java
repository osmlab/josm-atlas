package org.openstreetmap.atlas;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

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
import org.openstreetmap.atlas.utilities.collections.Maps;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jgage
 */
public class AtlasDataSetBuilder
{
    // Version of OsmPrimitive, required when setting OsmId in OpenStreetMap
    private static int IDENTIFIER_VERSION = 1;
    private static Bounds bounds;
    private static Logger logger = LoggerFactory.getLogger(AtlasDataSetBuilder.class);

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
    public DataSet build(final Atlas atlas, final ProgressMonitor monitor)
    {
        final Map<Location, org.openstreetmap.josm.data.osm.Node> nodeMap = new HashMap<>();
        monitor.setCustomText("Converting nodes...");
        final DataSet dataSet = new DataSet();
        for (final Node node : atlas.nodes())
        {
            addOsmNode(dataSet, monitor, node.getIdentifier(), node.getLocation(), node.getTags(),
                    node.relations(), nodeMap);

        }
        monitor.setCustomText("Converting points...");
        for (final Point point : atlas.points())
        {
            addOsmNode(dataSet, monitor, point.getIdentifier(), point.getLocation(),
                    point.getTags(), point.relations(), nodeMap);
        }
        monitor.setCustomText("Converting edges...");
        buildShapePoints(dataSet, monitor,
                Iterables.stream(atlas.edges()).map(Edge::asPolyLine).collect(), nodeMap);
        for (final Edge edge : atlas.edges())
        {
            final Way way = new Way();
            // Create the nodes that come from polyLine shapepoints
            final List<org.openstreetmap.josm.data.osm.Node> nodes = new ArrayList<>();
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
                final org.openstreetmap.josm.data.osm.Node node = nodeMap.get(location);
                nodes.add(node);
            }
            way.setNodes(nodes);
            final TreeMap<String, String> tagMap = new TreeMap<>();
            tagMap.putAll(edge.getTags());
            tagMap.put("first_lat_lon", edge.start().getLocation().toString());
            final int index = 0;
            for (final Relation relation : edge.relations())
            {
                tagMap.put("relation_" + index, String.valueOf(relation.getIdentifier()));
            }
            if (edge.hasReverseEdge())
            {
                tagMap.put("is_two_way", "yes");
            }
            way.setKeys(tagMap);
            // only takes positive direction because OSM doesn't allow negative ID's and only one
            // direction is required for visualization
            if (edge.getIdentifier() > 0)
            {
                way.setOsmId(edge.getIdentifier(), IDENTIFIER_VERSION);
                try
                {
                    dataSet.addPrimitive(way);
                }
                catch (final Exception e)
                {
                    // JOSM will throw an exception complaining that ways were added to the dataset
                    // without the nodes that comprise them
                    System.out.println("Unable to add OSM way " + way + " " + e.getMessage());
                }
            }
        }
        monitor.setCustomText("Converting lines...");
        buildShapePoints(dataSet, monitor,
                Iterables.stream(atlas.lines()).map(Line::asPolyLine).collect(), nodeMap);
        for (final Line line : atlas.lines())
        {
            final Way way = new Way();
            final List<org.openstreetmap.josm.data.osm.Node> nodes = new ArrayList<>();
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
                final org.openstreetmap.josm.data.osm.Node node = nodeMap.get(location);
                nodes.add(node);
            }
            way.setNodes(nodes);
            final TreeMap<String, String> tagMap = new TreeMap<>();
            tagMap.putAll(line.getTags());
            tagMap.put("first_lat_lon", line.getRawGeometry().iterator().next().toString());
            final int index = 0;
            for (final Relation relation : line.relations())
            {
                tagMap.put("relation_" + index, String.valueOf(relation.getIdentifier()));
            }
            way.setKeys(tagMap);
            if (line.getIdentifier() > 0)
            {
                way.setOsmId(line.getIdentifier(), IDENTIFIER_VERSION);
            }
            try
            {
                dataSet.addPrimitive(way);
            }
            catch (final Exception e)
            {
                logger.error("Already has: {}",
                        dataSet.getPrimitiveById(line.getOsmIdentifier(), OsmPrimitiveType.WAY), e);
            }
        }
        monitor.setCustomText("Converting areas...");
        for (final Area area : atlas.areas())
        {
            final Way way = new Way();
            final List<org.openstreetmap.josm.data.osm.Node> nodes = new ArrayList<>();
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
                final org.openstreetmap.josm.data.osm.Node node = new org.openstreetmap.josm.data.osm.Node(
                        latlon);
                dataSet.addPrimitive(node);
                nodes.add(node);
            }
            // first node added again so JOSM draws area
            final Location lastlocation = polygon.first();
            final Double lastNodeLat = lastlocation.getLatitude().asDegrees();
            final Double lastNodeLon = lastlocation.getLongitude().asDegrees();
            final LatLon lastLatLon = new LatLon(lastNodeLat, lastNodeLon);
            final org.openstreetmap.josm.data.osm.Node lastNode = new org.openstreetmap.josm.data.osm.Node(
                    lastLatLon);
            dataSet.addPrimitive(lastNode);
            nodes.add(lastNode);
            way.setNodes(nodes);
            final TreeMap<String, String> tagMap = new TreeMap<>();
            tagMap.putAll(area.getTags());
            tagMap.put("first_lat_lon", area.getRawGeometry().iterator().next().toString());
            final int index = 0;
            for (final Relation relation : area.relations())
            {
                tagMap.put("relation_" + index, String.valueOf(relation.getIdentifier()));
            }
            way.setKeys(tagMap);
            way.setOsmId(Math.abs(area.getIdentifier()), IDENTIFIER_VERSION);
            dataSet.addPrimitive(way);
        }
        monitor.setCustomText("Converting relations.");
        for (final Relation relation : atlas.relations())
        {
            final org.openstreetmap.josm.data.osm.Relation osmRelation = new org.openstreetmap.josm.data.osm.Relation();
            final List<org.openstreetmap.josm.data.osm.RelationMember> memberList = new ArrayList<>();
            final TreeMap<String, String> tagMap = new TreeMap<>();
            tagMap.putAll(relation.getTags());
            int index = 0;
            for (final RelationMember member : relation.members())
            {
                final String role = member.getRole();
                final OsmPrimitive primitive;
                if (member.getEntity() instanceof LocationItem)
                {
                    // Node & Point
                    primitive = dataSet.getPrimitiveById(member.getEntity().getIdentifier(),
                            OsmPrimitiveType.NODE);
                }
                else if (member.getEntity() instanceof AtlasItem)
                {
                    // Edge, Area, Line
                    primitive = dataSet.getPrimitiveById(member.getEntity().getIdentifier(),
                            OsmPrimitiveType.WAY);
                }
                else
                {
                    // Relation
                    primitive = dataSet.getPrimitiveById(member.getEntity().getIdentifier(),
                            OsmPrimitiveType.RELATION);
                }
                if (primitive != null)
                {
                    tagMap.put("member_" + index,
                            String.valueOf(member.getEntity().getIdentifier()));
                    index++;
                    final org.openstreetmap.josm.data.osm.RelationMember osmRelationMem = new org.openstreetmap.josm.data.osm.RelationMember(
                            role, primitive);
                    memberList.add(osmRelationMem);
                }

            }
            osmRelation.setMembers(memberList);
            final long relationId = relation.getIdentifier();
            monitor.setCustomText("Adding relation " + relationId + " to the data set...");
            osmRelation.setOsmId(relationId, IDENTIFIER_VERSION);
            osmRelation.setKeys(tagMap);
            dataSet.addPrimitive(osmRelation);
        }
        monitor.setCustomText(
                "Done adding atlas objects to data set. Please wait for layer to build...");
        return dataSet;
    }

    public Bounds getBounds()
    {
        return bounds;
    }

    private org.openstreetmap.josm.data.osm.Node addOsmNode(final DataSet dataSet,
            final ProgressMonitor monitor, final long identifier, final Location location,
            final Map<String, String> tags, final Iterable<Relation> relations,
            final Map<Location, org.openstreetmap.josm.data.osm.Node> nodeMap)
    {
        if (nodeMap.containsKey(location))
        {
            return nodeMap.get(location);
        }
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
        final org.openstreetmap.josm.data.osm.Node nodeOSM = new org.openstreetmap.josm.data.osm.Node(
                latlon);
        if (identifier > 0)
        {
            final TreeMap<String, String> tagMap = new TreeMap<>();
            tagMap.putAll(tags);
            tagMap.put("lat_lon", location.toString());
            final int index = 0;
            for (final Relation relation : relations)
            {
                tagMap.put("relation_" + index, String.valueOf(relation.getIdentifier()));
            }
            nodeOSM.setKeys(tagMap);
            nodeOSM.setOsmId(identifier, IDENTIFIER_VERSION);
        }
        try
        {
            dataSet.addPrimitive(nodeOSM);
            nodeMap.put(location, nodeOSM);
        }
        catch (final Exception e)
        {
            monitor.setCustomText(
                    "Already has: " + dataSet.getPrimitiveById(identifier, OsmPrimitiveType.NODE));
        }
        return nodeOSM;
    }

    private void buildShapePoints(final DataSet dataSet, final ProgressMonitor monitor,
            final Iterable<PolyLine> polyLines,
            final Map<Location, org.openstreetmap.josm.data.osm.Node> nodeMap)
    {
        for (final PolyLine polyLine : polyLines)
        {
            for (final Location shapePoint : polyLine)
            {
                addOsmNode(dataSet, monitor, -1, shapePoint, Maps.stringMap(), new ArrayList<>(),
                        nodeMap);
            }
        }
    }
}
