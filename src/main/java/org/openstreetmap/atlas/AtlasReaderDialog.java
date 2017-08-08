package org.openstreetmap.atlas;

import java.awt.BorderLayout;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;
import java.util.TreeSet;

import javax.swing.AbstractAction;
import javax.swing.Action;
import javax.swing.DefaultListModel;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JList;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.KeyStroke;

import org.openstreetmap.atlas.AtlasSearch.SearchType;
import org.openstreetmap.atlas.exception.CoreException;
import org.openstreetmap.atlas.geography.Latitude;
import org.openstreetmap.atlas.geography.Location;
import org.openstreetmap.atlas.geography.Longitude;
import org.openstreetmap.atlas.geography.Snapper;
import org.openstreetmap.atlas.geography.atlas.items.Area;
import org.openstreetmap.atlas.geography.atlas.items.AtlasItem;
import org.openstreetmap.atlas.geography.atlas.items.Edge;
import org.openstreetmap.atlas.geography.atlas.items.ItemType;
import org.openstreetmap.atlas.geography.atlas.items.Line;
import org.openstreetmap.atlas.geography.atlas.items.Point;
import org.openstreetmap.atlas.geography.atlas.packed.PackedEdge;
import org.openstreetmap.atlas.utilities.scalars.Distance;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.coor.LatLon;
import org.openstreetmap.josm.data.osm.Node;
import org.openstreetmap.josm.data.osm.OsmPrimitive;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.Relation;
import org.openstreetmap.josm.data.osm.Way;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.dialogs.DialogsPanel;
import org.openstreetmap.josm.gui.dialogs.ToggleDialog;
import org.openstreetmap.josm.gui.history.HistoryBrowserDialogManager;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerAddEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerChangeListener;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerOrderChangeEvent;
import org.openstreetmap.josm.gui.layer.LayerManager.LayerRemoveEvent;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;

/**
 * @author jgage
 */

public class AtlasReaderDialog extends ToggleDialog implements LayerChangeListener
{
    /**
     * @author jgage
     */
    public static class PrintablePrimitive
    {
        private final OsmPrimitive osmPrimitive;
        private final long index;

        public PrintablePrimitive(final long index, final OsmPrimitive osmPrimitive)
        {
            this.osmPrimitive = osmPrimitive;
            this.index = index;
        }

        public long getIndex()
        {
            return this.index;
        }

        public OsmPrimitive getOsmPrimitive()
        {
            return this.osmPrimitive;
        }

        @Override
        public String toString()
        {
            final StringBuilder result = new StringBuilder();
            result.append("Index: ");
            result.append(this.index);
            result.append(", ID: ");
            result.append(this.osmPrimitive.getPrimitiveId());
            return result.toString();
        }
    }

    /**
     * Class that stores minimum distance between an AtlasItem and a location.
     *
     * @author jgage
     * @author matthieun
     */
    public static class SnappedEntity implements Comparable<SnappedEntity>
    {
        private static final Distance POINT_PREVALENCE_DISTANCE = Distance.meters(2);

        private final AtlasItem item;
        private final Distance distance;
        private Iterable<? extends Location> geometry;

        public SnappedEntity(final AtlasItem item, final Location location)
        {
            this.item = item;
            if (item instanceof Area)
            {
                this.geometry = ((Area) item).asPolygon();
            }
            else if (item instanceof Line)
            {
                this.geometry = ((Line) item).asPolyLine();
            }
            else if (item instanceof Point)
            {
                this.geometry = ((Point) item).getLocation();
            }
            else if (item instanceof Edge || item instanceof PackedEdge)
            {
                this.geometry = ((Edge) item).asPolyLine();
            }
            else if (item instanceof org.openstreetmap.atlas.geography.atlas.items.Node)
            {
                this.geometry = ((org.openstreetmap.atlas.geography.atlas.items.Node) item)
                        .getLocation();
            }
            else
            {
                throw new CoreException("Unrecognized type {}", item.getClass());
            }
            this.distance = new Snapper().snap(location, this.geometry).getDistance();
        }

        @Override
        public int compareTo(final SnappedEntity object)
        {
            if (object.getItem().getType() == ItemType.NODE
                    || object.getItem().getType() == ItemType.POINT)
            {
                if (this.getItem().getType() != ItemType.NODE
                        && this.getItem().getType() != ItemType.POINT)
                {
                    if (object.getDistance().isLessThan(POINT_PREVALENCE_DISTANCE))
                    {
                        // Object is a Point/Node, and this is not. Give precedence to object
                        return 1;
                    }
                }
            }
            if (this.getItem().getType() == ItemType.NODE
                    || this.getItem().getType() == ItemType.POINT)
            {
                if (object.getItem().getType() != ItemType.NODE
                        && object.getItem().getType() != ItemType.POINT)
                {
                    if (this.getDistance().isLessThan(POINT_PREVALENCE_DISTANCE))
                    {
                        // This is a Point/Node, and object is not. Give precedence to this
                        return -1;
                    }
                }
            }
            if (object.getDistance().isGreaterThan(this.getDistance()))
            {
                return -1;
            }
            if (object.getDistance().equals(this.getDistance()))
            {
                return 0;
            }
            else
            {
                return 1;
            }
        }

        public Distance getDistance()
        {
            return this.distance;
        }

        public AtlasItem getItem()
        {
            return this.item;
        }

    }

    private static boolean listClick = false;
    private static final long serialVersionUID = 2182365950017249421L;
    private static final int num = 150;
    private static OsmPrimitive previous;
    private static final int TEXT_FIELD_LENGTH = 15;

    private final AtlasReaderLayer layer;
    private final JPanel panel;
    private JTable tagBox = null;
    private JList<PrintablePrimitive> list;
    private DefaultListModel<PrintablePrimitive> listAll;
    private JScrollPane listPane;
    private BiMap<Integer, PrimitiveId> indexToIdentifierAll;
    private int selectedIndex;
    private int listLength;
    private DefaultListModel<PrintablePrimitive> previousResults;

    private static String allLetters(final String charsetName)
    {
        final CharsetEncoder charsetEncoder = Charset.forName(charsetName).newEncoder();
        final StringBuilder result = new StringBuilder();
        for (char character = 0; character < Character.MAX_VALUE; character++)
        {
            if (charsetEncoder.canEncode(character))
            {
                result.append(character);
            }
        }
        return result.toString();
    }

    /**
     * Constructor initializes dialog and adds listeners for ListView and MapView.
     * 
     * @param layer
     *            The corresponding layer
     */
    public AtlasReaderDialog(final AtlasReaderLayer layer)
    {
        super("Atlas: " + layer.getAtlas().getName(), "world.png",
                "Opens the AtlasReader plugin pane.", null, num);
        this.layer = layer;
        this.panel = new JPanel(new BorderLayout());
        this.panel.setName("AtlasReader Panel");
        if (layer != null && layer.getData() != null && layer.getData().allPrimitives() != null
                && !layer.getData().allPrimitives().isEmpty())
        {
            add(this.panel, BorderLayout.CENTER);

            final DefaultListModel<PrintablePrimitive> model = new DefaultListModel<>();
            final BiMap<Integer, PrimitiveId> indexToIdentifier = HashBiMap.create();
            int index = 0;

            for (final OsmPrimitive osmPrimitive : layer.getData().allPrimitives())
            {
                if (osmPrimitive instanceof Node)
                {
                    if (osmPrimitive.getKeys().isEmpty())
                    {
                        continue;
                    }
                }
                model.addElement(new PrintablePrimitive(index, osmPrimitive));
                indexToIdentifier.put(index, osmPrimitive.getPrimitiveId());
                index++;
            }
            this.list = new JList<>(model);
            this.listAll = model;
            this.indexToIdentifierAll = indexToIdentifier;
            createListListeners(indexToIdentifier);
            createMapListener(indexToIdentifier);
            initializePanel(indexToIdentifier);
            this.listPane = new JScrollPane(this.list);
            this.panel.add(this.listPane, BorderLayout.CENTER);
            this.titleBar = new TitleBar(this.name, "world.png");
            this.titleBar.registerMouseListener();
            add(this.titleBar, BorderLayout.NORTH);
            Main.getLayerManager().addLayerChangeListener(this);
        }
    }

    @Override
    public void layerAdded(final LayerAddEvent e)
    {

    }

    @Override
    public void layerOrderChanged(final LayerOrderChangeEvent e)
    {

    }

    @Override
    public void layerRemoving(final LayerRemoveEvent e)
    {
        if (e.getRemovedLayer() == this.layer)
        {
            this.panel.removeAll();
            this.panel.revalidate();
            this.panel.repaint();
            final DialogsPanel dialogPanel = this.dialogsPanel;
            dialogPanel.remove(this);
            this.setDialogsPanel(dialogPanel);
        }

    }

    private void clearButtonInit(final JButton clearButton, final JTextField searchText)
    {
        final Action clearResults = new AbstractAction()
        {

            private static final long serialVersionUID = 199864769664334L;

            @Override
            public void actionPerformed(final ActionEvent e)
            {
                final AtlasSearch searcher = new AtlasSearch(
                        AtlasReaderDialog.this.layer.getAtlas(),
                        AtlasReaderDialog.this.layer.getData(), SearchType.ALL,
                        AtlasReaderDialog.this.listAll,
                        AtlasReaderDialog.this.indexToIdentifierAll);
                final DefaultListModel<PrintablePrimitive> results;
                results = searcher.search("");
                if (results != null)
                {
                    // update ListView with search results
                    AtlasReaderDialog.this.panel.remove(AtlasReaderDialog.this.listPane);
                    AtlasReaderDialog.this.list = new JList<>(results);
                    AtlasReaderDialog.this.listLength = results.size();
                    AtlasReaderDialog.this.listPane = new JScrollPane(AtlasReaderDialog.this.list);
                    AtlasReaderDialog.this.panel.add(AtlasReaderDialog.this.listPane);
                    AtlasReaderDialog.this.panel.revalidate();
                    AtlasReaderDialog.this.panel.repaint();

                    // unhighlight all results
                    if (AtlasReaderDialog.this.previousResults != null)
                    {
                        for (int i = 0; i < AtlasReaderDialog.this.previousResults.size(); i++)
                        {
                            AtlasReaderDialog.this.previousResults.get(i).osmPrimitive
                                    .setHighlighted(false);
                        }
                    }
                    AtlasReaderDialog.this.layer.getData().setSelected();

                    // recreate list listeners with new list index (results of search)
                    createListListeners(searcher.getIndexToIdentifier());
                    for (final MouseListener mouseListener : Main.map.mapView.getMouseListeners())
                    {
                        Main.map.mapView.removeMouseListener(mouseListener);
                    }
                    createMapListener(searcher.getIndexToIdentifier());
                }
                searchText.setText("");
            }
        };
        clearButton.addActionListener(clearResults);
    }

    /**
     * Handles the logic of a list click.
     */
    private void createListListeners(final BiMap<Integer, PrimitiveId> indexToIdentifier)
    {
        this.list.addMouseListener(new MouseAdapter()
        {

            @Override
            public void mouseClicked(final MouseEvent event)
            {
                listClick = true;
                AtlasReaderDialog.this.selectedIndex = AtlasReaderDialog.this.list
                        .locationToIndex(event.getPoint());
                final PrimitiveId identifier = indexToIdentifier
                        .get(AtlasReaderDialog.this.selectedIndex);
                AtlasReaderDialog.this.layer.getData().setSelected(identifier);
                if (identifier != null)
                {
                    zoomTo(AtlasReaderDialog.this.layer.getData().getPrimitiveById(identifier));
                    createTagBox(
                            AtlasReaderDialog.this.layer.getData().getPrimitiveById(identifier));
                }
            }
        });
        this.list.addListSelectionListener(listSelectionEvent ->
        {
            if (listClick)
            {
                final int index = listSelectionEvent.getFirstIndex();
                final PrimitiveId identifier = indexToIdentifier.get(index);
                this.layer.getData().setSelected(identifier);
                zoomTo(this.layer.getData().getPrimitiveById(identifier));
                createTagBox(this.layer.getData().getPrimitiveById(identifier));
            }
        });

        final Action downAction = new AbstractAction()
        {
            private static final long serialVersionUID = 23452349529387L;

            @Override
            public void actionPerformed(final ActionEvent e)
            {
                // ensures index is less than results size and all items in dataset
                if (AtlasReaderDialog.this.selectedIndex != AtlasReaderDialog.this.listLength - 1
                        && AtlasReaderDialog.this.selectedIndex != indexToIdentifier.size())
                {
                    AtlasReaderDialog.this.selectedIndex += 1;
                }
                AtlasReaderDialog.this.list.setSelectedIndex(AtlasReaderDialog.this.selectedIndex);
                final PrimitiveId identifier = indexToIdentifier
                        .get(AtlasReaderDialog.this.selectedIndex);
                AtlasReaderDialog.this.layer.getData().setSelected(identifier);
                zoomTo(AtlasReaderDialog.this.layer.getData().getPrimitiveById(identifier));
                createTagBox(AtlasReaderDialog.this.layer.getData().getPrimitiveById(identifier));
                AtlasReaderDialog.this.list
                        .ensureIndexIsVisible(AtlasReaderDialog.this.selectedIndex);
            }
        };
        final Action upAction = new AbstractAction()
        {
            private static final long serialVersionUID = 982345792348755L;

            @Override
            public void actionPerformed(final ActionEvent e)
            {
                if (AtlasReaderDialog.this.selectedIndex != 0)
                {
                    AtlasReaderDialog.this.selectedIndex -= 1;
                }
                AtlasReaderDialog.this.list.setSelectedIndex(AtlasReaderDialog.this.selectedIndex);
                final PrimitiveId identifier = indexToIdentifier
                        .get(AtlasReaderDialog.this.selectedIndex);
                AtlasReaderDialog.this.layer.getData().setSelected(identifier);
                zoomTo(AtlasReaderDialog.this.layer.getData().getPrimitiveById(identifier));
                createTagBox(AtlasReaderDialog.this.layer.getData().getPrimitiveById(identifier));
                AtlasReaderDialog.this.list
                        .ensureIndexIsVisible(AtlasReaderDialog.this.selectedIndex);
            }
        };
        this.list.getInputMap().put(KeyStroke.getKeyStroke("DOWN"), "downAction");
        this.list.getActionMap().put("downAction", downAction);
        this.list.getInputMap().put(KeyStroke.getKeyStroke("UP"), "upAction");
        this.list.getActionMap().put("upAction", upAction);

    }

    /**
     * Handles the logic of a map click.
     */
    private void createMapListener(final BiMap<Integer, PrimitiveId> indexToIdentifier)
    {
        Main.map.mapView.addMouseListener(new MouseAdapter()
        {
            @Override
            public void mouseClicked(final MouseEvent event)
            {
                if (previous != null)
                {
                    previous.setHighlighted(false);
                }
                listClick = false;
                final LatLon latlon = Main.map.mapView.getLatLon(event.getX(), event.getY());
                final Location location = new Location(Latitude.degrees(latlon.lat()),
                        Longitude.degrees(latlon.lon()));
                final TreeSet<SnappedEntity> itemsNearClick = new TreeSet<>();
                final int clickRadiusInMeters = 200;
                // stores all items near click point and uses comparator to order them, the closest
                // being the first element
                if (AtlasReaderDialog.this.layer.getAtlas() != null)
                {
                    for (final AtlasItem item : AtlasReaderDialog.this.layer.getAtlas()
                            .itemsIntersecting(
                                    location.boxAround(Distance.meters(clickRadiusInMeters))))
                    {
                        final SnappedEntity snap = new SnappedEntity(item, location);
                        itemsNearClick.add(snap);
                    }
                }
                AtlasItem item = null;
                if (!itemsNearClick.isEmpty())
                {
                    item = itemsNearClick.first().item;
                }
                if (item == null)
                {
                    AtlasReaderDialog.this.layer.getData().setSelected();
                }
                OsmPrimitive selected = null;
                // sets selected primitive
                if (item instanceof org.openstreetmap.atlas.geography.atlas.items.Node
                        || item instanceof Point)
                {
                    selected = AtlasReaderDialog.this.layer.getData()
                            .getPrimitiveById(item.getIdentifier(), OsmPrimitiveType.NODE);
                }
                if (item instanceof Area || item instanceof Edge || item instanceof Line)
                {
                    selected = AtlasReaderDialog.this.layer.getData()
                            .getPrimitiveById(item.getIdentifier(), OsmPrimitiveType.WAY);
                }
                // highlights selection on map and in list
                if (selected != null)
                {
                    selected.setHighlighted(true);
                    AtlasReaderDialog.this.layer.getData().setSelected(selected.getPrimitiveId());
                    // Main.map.mapView.repaint();
                    AtlasReaderDialog.this.layer.invalidate();
                    try
                    {
                        final int index = indexToIdentifier.inverse()
                                .get(selected.getPrimitiveId());
                        AtlasReaderDialog.this.list.setSelectedIndex(index);
                        AtlasReaderDialog.this.list.ensureIndexIsVisible(index);
                    }
                    catch (final Exception e)
                    {
                        e.printStackTrace();
                    }
                    previous = selected;
                    createTagBox(selected);
                }
            }
        });
    }

    /*
     * Converts tagMap to 2d array and creates/updates the Tag Box.
     */
    private void createTagBox(final OsmPrimitive selected)
    {
        if (this.tagBox != null)
        {
            this.panel.remove(this.tagBox);
        }
        final Set<String> keys = selected.getKeys().keySet();
        final Iterator<String> keyIterator = keys.iterator();
        final Iterator<String> valuesIterator = selected.getKeys().values().iterator();
        final String[] columnNames = { "Key", "Value" };
        final String[][] data = new String[keys.size() + 1][2];
        data[0][0] = "atlas_identifier";
        data[0][1] = String.valueOf(selected.getId());
        for (int i = 1; i < keys.size() + 1; i++)
        {
            data[i][0] = keyIterator.next();
            data[i][1] = valuesIterator.next();
        }
        this.tagBox = new JTable(data, columnNames);
        this.panel.add(this.tagBox, BorderLayout.SOUTH);
        this.tagBox.revalidate();
        this.tagBox.repaint();
    }

    private void historyButtonInit(final JButton historyButton)
    {
        historyButton.addActionListener(event ->
        {
            final Collection<OsmPrimitive> selections = this.layer.getData().getSelected();
            if (!selections.isEmpty())
            {
                final OsmPrimitive selected = selections.iterator().next();
                final long identifier = selected.getId();
                final String identifierString = String.valueOf(identifier);
                final Collection<OsmPrimitive> lookup = new ArrayList<>();
                if (selected instanceof Node)
                {
                    final long newIdentifier = Long.parseLong(identifierString.substring(0, 10));
                    final Node node = new Node();
                    final int version = 1;
                    node.setOsmId(newIdentifier, version);
                    lookup.add(node);
                }
                if (selected instanceof Way)
                {
                    final long newIdentifier = Long.parseLong(identifierString.substring(0, 9));
                    final Way way = new Way();
                    final int version = 1;
                    way.setOsmId(newIdentifier, version);
                    lookup.add(way);
                }
                HistoryBrowserDialogManager.getInstance().showHistory(lookup);
            }
        });
    }

    /*
     * Helper function that lays out dialog window correctly.
     */
    private void initializePanel(final BiMap<Integer, PrimitiveId> indexToIdentifier)
    {
        final JTextField searchText = new JTextField(TEXT_FIELD_LENGTH);
        final JButton searchButton = new JButton("Search");
        final JButton historyButton = new JButton("History");
        historyButtonInit(historyButton);
        final JButton metaDataButton = new JButton("Meta Data");
        final JButton clearButton = new JButton("Clear Results");
        final JButton showAll = new JButton("Show All");
        clearButtonInit(clearButton, searchText);
        showAllInit(showAll);
        metaDataButton.addActionListener(event ->
        {
            final JFrame parent = new JFrame();
            final String[] metaData = this.layer.getAtlas().metaData().toString().split(",");
            JOptionPane.showMessageDialog(parent, metaData, "Atlas MetaData",
                    JOptionPane.INFORMATION_MESSAGE);
        });
        final JComboBox<String> searchOptions = new JComboBox<>();
        final String[] options = { SearchType.TAG.getName(), SearchType.OSM_IDENTIFIER.getName(),
                SearchType.ATLAS_IDENTIFIER.getName(), SearchType.BOX.getName() };
        for (int i = 0; i < options.length; i++)
        {
            searchOptions.addItem(options[i]);
        }
        // layout
        searchText.setText("Search...");
        searchText.setEditable(true);
        final JPanel searchPanel = new JPanel(new BorderLayout());
        final JPanel searchButtons = new JPanel(new BorderLayout());
        final JPanel extraButtons = new JPanel(new BorderLayout());
        final JPanel middleButtons = new JPanel(new BorderLayout());
        searchButtons.add(searchOptions, BorderLayout.WEST);
        searchButtons.add(searchButton, BorderLayout.EAST);
        extraButtons.add(historyButton, BorderLayout.WEST);
        extraButtons.add(metaDataButton, BorderLayout.EAST);
        middleButtons.add(clearButton, BorderLayout.WEST);
        middleButtons.add(showAll, BorderLayout.EAST);
        extraButtons.add(middleButtons, BorderLayout.CENTER);
        searchPanel.add(extraButtons, BorderLayout.SOUTH);
        searchPanel.add(searchText, BorderLayout.CENTER);
        searchPanel.add(searchButtons, BorderLayout.EAST);
        this.panel.add(searchPanel, BorderLayout.NORTH);
        searchListenerInit(searchButton, searchOptions, searchText);
    }

    /*
     * Handles logic of searchButton click
     */
    private void searchListenerInit(final JButton searchButton,
            final JComboBox<String> searchOptions, final JTextField searchText)
    {
        final Action searchAction = new AbstractAction()
        {

            private static final long serialVersionUID = 23049587234908L;

            @Override
            public void actionPerformed(final ActionEvent e)
            {
                final AtlasSearch searcher = new AtlasSearch(
                        AtlasReaderDialog.this.layer.getAtlas(),
                        AtlasReaderDialog.this.layer.getData(),
                        SearchType.forName(searchOptions.getSelectedItem().toString()),
                        AtlasReaderDialog.this.listAll,
                        AtlasReaderDialog.this.indexToIdentifierAll);
                final DefaultListModel<PrintablePrimitive> results;
                results = searcher.search(searchText.getText());
                if (results != null)
                {
                    // update ListView with search results
                    AtlasReaderDialog.this.panel.remove(AtlasReaderDialog.this.listPane);
                    AtlasReaderDialog.this.list = new JList<>(results);
                    AtlasReaderDialog.this.listLength = results.size();
                    AtlasReaderDialog.this.listPane = new JScrollPane(AtlasReaderDialog.this.list);
                    AtlasReaderDialog.this.panel.add(AtlasReaderDialog.this.listPane);
                    AtlasReaderDialog.this.panel.revalidate();
                    AtlasReaderDialog.this.panel.repaint();

                    // show entire Atlas
                    final BoundingXYVisitor visitor = new BoundingXYVisitor();
                    visitor.computeBoundingBox(
                            AtlasReaderDialog.this.layer.getData().allPrimitives());
                    Main.map.mapView.zoomTo(visitor.getBounds());

                    // highlight all search results, unless "all" is the mode
                    if (!"All".equals(searchOptions.getSelectedItem().toString()))
                    {
                        if (AtlasReaderDialog.this.previousResults != null)
                        {
                            for (int i = 0; i < AtlasReaderDialog.this.previousResults.size(); i++)
                            {
                                AtlasReaderDialog.this.previousResults.get(i).osmPrimitive
                                        .setHighlighted(false);
                            }
                        }
                        final ArrayList<PrimitiveId> toBeSelected = new ArrayList<>();
                        for (int i = 0; i < results.size(); i++)
                        {
                            final OsmPrimitive result = results.get(i).osmPrimitive;
                            result.setHighlighted(true);
                            toBeSelected.add(result.getPrimitiveId());
                        }
                        AtlasReaderDialog.this.layer.getData().setSelected(toBeSelected);
                        Main.map.mapView.revalidate();
                        Main.map.mapView.repaint();
                        AtlasReaderDialog.this.previousResults = results;
                    }

                    // recreate list listeners with new list index (results of search)
                    createListListeners(searcher.getIndexToIdentifier());
                    for (final MouseListener mouseListener : Main.map.mapView.getMouseListeners())
                    {
                        Main.map.mapView.removeMouseListener(mouseListener);
                    }
                    createMapListener(searcher.getIndexToIdentifier());
                }
            }
        };
        final Action doNothing = new AbstractAction()
        {
            private static final long serialVersionUID = 980234750932847L;

            @Override
            public void actionPerformed(final ActionEvent e)
            {

            }
        };
        final char[] charAlphabet = allLetters("US-ASCII").toCharArray();
        final String[] stringAlphabet = new String[charAlphabet.length];
        for (int i = 0; i < charAlphabet.length; i++)
        {
            stringAlphabet[i] = String.valueOf(charAlphabet[i]);
        }
        for (final String key : stringAlphabet)
        {
            searchText.getInputMap().put(
                    KeyStroke.getKeyStroke(key.charAt(0), InputEvent.SHIFT_DOWN_MASK), "doNothing");
            searchText.getInputMap().put(KeyStroke.getKeyStroke(key), "doNothing");
        }
        searchText.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, 0), "doNothing");
        searchText.getInputMap().put(KeyStroke.getKeyStroke("ENTER"), "searchAction");
        searchText.getActionMap().put("searchAction", searchAction);
        searchText.getActionMap().put("doNothing", doNothing);
        searchButton.addActionListener(searchAction);
    }

    private void showAllInit(final JButton showAll)
    {
        final Action showEntireAtlas = new AbstractAction()
        {

            private static final long serialVersionUID = 199864769664334L;

            @Override
            public void actionPerformed(final ActionEvent e)
            {
                // show entire Atlas
                final BoundingXYVisitor visitor = new BoundingXYVisitor();
                visitor.computeBoundingBox(AtlasReaderDialog.this.layer.getData().allPrimitives());
                Main.map.mapView.zoomTo(visitor.getBounds());
            }
        };
        showAll.addActionListener(showEntireAtlas);
    }

    private void zoomTo(final OsmPrimitive primitive)
    {
        final BoundingXYVisitor visitor = new BoundingXYVisitor();
        if (primitive instanceof Way)
        {
            visitor.visit((Way) primitive);
        }

        if (primitive instanceof Node)
        {
            visitor.visit((Node) primitive);
        }
        if (primitive instanceof Relation)
        {
            visitor.visit((Relation) primitive);
        }
        if (visitor.getBounds() != null)
        {
            Main.map.mapView.zoomTo(visitor.getBounds());
        }
    }
}
