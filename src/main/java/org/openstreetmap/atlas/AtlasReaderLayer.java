package org.openstreetmap.atlas;

import java.awt.Graphics2D;

import javax.swing.Action;
import javax.swing.Icon;

import org.openstreetmap.atlas.geography.atlas.Atlas;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.data.osm.visitor.paint.MapRendererFactory;
import org.openstreetmap.josm.data.osm.visitor.paint.Rendering;
import org.openstreetmap.josm.gui.MapView;
import org.openstreetmap.josm.gui.layer.AbstractModifiableLayer;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * @author jgage
 */
public class AtlasReaderLayer extends AbstractModifiableLayer
{
    private static String LAYER_NAME = "Atlas Layer";

    private Atlas atlas;
    private final DataSet dataSet;
    private final Bounds bounds;

    public AtlasReaderLayer(final String info, final DataSet data, final Atlas atlas,
            final Bounds bounds)
    {
        super(info);
        this.dataSet = data;
        this.atlas = atlas;
        this.bounds = bounds;
    }

    @Override
    public void destroy()
    {
        this.atlas = null;
    }

    public Atlas getAtlas()
    {
        return this.atlas;
    }

    public DataSet getData()
    {
        return this.dataSet;
    }

    @Override
    public Icon getIcon()
    {
        return ImageProvider.get("dialogs", "world");
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
    public boolean isMergable(final Layer arg0)
    {
        return false;
    }

    /**
     * Atlas should be treated as immutable.
     *
     * @return Always false
     */
    @Override
    public final boolean isModified()
    {
        return false;
    }

    /**
     * We should not be able to merge layers.
     *
     * @param layer
     *            The layer parameter is ignored
     */
    @Override
    public void mergeFrom(final Layer layer)
    {
    }

    /**
     * Paints the dataSet comprised of the OSM equivalents of atlas objects.
     */
    @Override
    public void paint(final Graphics2D graphics, final MapView mapView, final Bounds bounds)
    {
        final boolean active = mapView.getLayerManager().getActiveLayer() == this;
        final boolean inactive = !active && Main.pref.getBoolean("draw.data.inactive_color", true);
        final boolean virtual = !inactive && mapView.isVirtualNodesEnabled();
        Main.pref.getBoolean("draw.data.inactive_color", false);
        final Rendering painter = MapRendererFactory.getInstance().createActiveRenderer(graphics,
                mapView, inactive);
        painter.render(this.dataSet, virtual, bounds);
    }

    @Override
    public void visitBoundingBox(final BoundingXYVisitor visitor)
    {
        visitor.visit(this.bounds);
    }
}
