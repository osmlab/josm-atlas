package org.openstreetmap.atlas;

import static org.openstreetmap.josm.tools.I18n.tr;

import javax.swing.Action;
import javax.swing.Icon;

import org.openstreetmap.atlas.geography.atlas.Atlas;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.data.osm.visitor.BoundingXYVisitor;
import org.openstreetmap.josm.gui.layer.Layer;
import org.openstreetmap.josm.gui.layer.OsmDataLayer;
import org.openstreetmap.josm.tools.ImageProvider;

/**
 * @author jgage
 */
public class AtlasReaderLayer extends OsmDataLayer
{
    private static final String LAYER_NAME = tr("Atlas Layer");

    private Atlas atlas;
    private final Bounds bounds;

    public AtlasReaderLayer(final String info, final DataSet data, final Atlas atlas,
            final Bounds bounds)
    {
        super(data, info, null);
        this.atlas = atlas;
        this.bounds = bounds;
        // Make sure dataset is read-only
        if (!data.isLocked())
        {
            data.lock();
        }
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
}
