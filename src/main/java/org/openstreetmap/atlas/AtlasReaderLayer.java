package org.openstreetmap.atlas;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Graphics2D;

import javax.swing.Action;
import javax.swing.Icon;

import org.openstreetmap.atlas.data.AtlasDataSet;
import org.openstreetmap.atlas.geography.atlas.Atlas;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSelectionListener;
import org.openstreetmap.josm.data.osm.event.SelectionEventManager;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.data.osm.visitor.paint.MapRendererFactory;
import org.openstreetmap.josm.data.osm.visitor.paint.Rendering;
import org.openstreetmap.josm.gui.MainApplication;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.AbstractOsmDataLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.spi.preferences.Config;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * @author jgage
 */
public class AtlasReaderLayer extends AbstractOsmDataLayer implements DataSelectionListener
{
    private static final String LAYER_NAME = tr("Atlas Layer");

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
        MainApplication.getLayerManager().addActiveLayerChangeListener(e ->
        {
            if (e.getSource().getActiveLayer() == AtlasReaderLayer.this)
            {
                data.addSelectionListener(SelectionEventManager.getInstance());
            }
            else 
            {
                data.removeSelectionListener(SelectionEventManager.getInstance());
            }
        });
    }

    @Override
    public void destroy()
    {
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
        final boolean inactive = !active && Config.getPref().getBoolean("draw.data.inactive_color", true);
        final boolean virtual = !inactive && map.isVirtualNodesEnabled();

        final Rendering painter = MapRendererFactory.getInstance().createActiveRenderer(g2d, map, inactive);
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
}
