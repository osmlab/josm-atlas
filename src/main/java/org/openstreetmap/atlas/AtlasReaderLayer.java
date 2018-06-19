package org.openstreetmap.atlas;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Graphics2D;
import java.util.List;
import java.util.ListIterator;

import javax.swing.Action;
import javax.swing.Icon;

import org.openstreetmap.atlas.data.AtlasDataSet;
import org.openstreetmap.atlas.geography.atlas.Atlas;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSelectionListener;
import org.openstreetmap.josm.data.osm.OsmPrimitiveType;
import org.openstreetmap.josm.data.osm.PrimitiveId;
import org.openstreetmap.josm.data.osm.SimplePrimitiveId;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.data.osm.visitor.paint.MapRendererFactory;
import org.openstreetmap.josm.data.osm.visitor.paint.Rendering;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.history.HistoryBrowserDialogManager;
import org.openstreetmap.josm.gui.history.HistoryHook;
import org.openstreetmap.josm.gui.layer.AbstractOsmDataLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeEvent;
import org.openstreetmap.josm.gui.layer.MainLayerManager.ActiveLayerChangeListener;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * @author jgage
 */
public class AtlasReaderLayer extends AbstractOsmDataLayer
        implements DataSelectionListener, ActiveLayerChangeListener, HistoryHook
{
    private static final String LAYER_NAME = tr("Atlas Layer");

    private static final int NINE = 9;
    private static final int TEN = 10;

    private Atlas atlas;
    private AtlasDataSet data;
    private final Bounds bounds;

    public AtlasReaderLayer(final String info, final AtlasDataSet data, final Atlas atlas,
            final Bounds bounds)
    {
        super(info);
        this.atlas = atlas;
        this.data = data;
        this.bounds = bounds;
        data.addSelectionListener(this);
        MainApplication.getLayerManager().addActiveLayerChangeListener(this);
        HistoryBrowserDialogManager.addHistoryHook(this);
    }

    @Override
    public void destroy()
    {
        HistoryBrowserDialogManager.removeHistoryHook(this);
        MainApplication.getLayerManager().removeActiveLayerChangeListener(this);
        data.removeSelectionListener(this);
        this.atlas = null;
        this.data = null;
    }

    public Atlas getAtlas()
    {
        return this.atlas;
    }

    @Override
    public AtlasDataSet getDataSet()
    {
        return this.data;
    }

    @Override
    public Icon getIcon()
    {
        return new ImageProvider("dialogs/world").setSize(ImageProvider.ImageSizes.LAYER).get();
    }

    @Override
    public Object getInfoComponent()
    {
        return null;
    }

    @Override
    public Action[] getMenuEntries()
    {
        return new Action[0];
    }

    @Override
    public String getToolTipText()
    {
        return LAYER_NAME;
    }

    @Override
    public boolean isMergable(final Layer other)
    {
        return false;
    }

    @Override
    public void visitBoundingBox(final BoundingXYVisitor visitor)
    {
        visitor.visit(this.bounds);
    }

    @Override
    public void paint(final Graphics2D g2d, final MapView map, final Bounds bbox)
    {
        final boolean active = map.getLayerManager().getActiveLayer() == this;
        final boolean inactive = !active
                && Config.getPref().getBoolean("draw.data.inactive_color", true);
        final boolean virtual = !inactive && map.isVirtualNodesEnabled();

        final Rendering painter = MapRendererFactory.getInstance().createActiveRenderer(g2d, map,
                inactive);
        painter.render(data, virtual, bbox);
    }

    @Override
    public boolean isModified()
    {
        return false;
    }

    @Override
    public void mergeFrom(final Layer from)
    {
        // Do nothing
    }

    @Override
    public void selectionChanged(final SelectionChangeEvent event)
    {
        invalidate();
    }

    @Override
    public void modifyRequestedIds(final List<PrimitiveId> ids)
    {
        for (final ListIterator<PrimitiveId> iter = ids.listIterator(); iter.hasNext();)
        {
            final PrimitiveId pid = iter.next();
            if (data.getPrimitiveById(pid) != null)
            {
                iter.set(new SimplePrimitiveId(
                        Long.parseLong(String.valueOf(pid.getUniqueId()).substring(0,
                                OsmPrimitiveType.NODE.equals(pid.getType()) ? TEN : NINE)),
                        pid.getType()));
            }
        }
    }

    @Override
    public void activeOrEditLayerChanged(final ActiveLayerChangeEvent e)
    {
        if (e.getSource().getActiveLayer() == AtlasReaderLayer.this)
        {
            data.addSelectionListener(SelectionEventManager.getInstance());
        }
        else
        {
            data.removeSelectionListener(SelectionEventManager.getInstance());
        }
    }
}
