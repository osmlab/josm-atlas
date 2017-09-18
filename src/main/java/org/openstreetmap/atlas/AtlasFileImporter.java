package org.openstreetmap.atlas;

import static org.openstreetmap.josm.tools.I18n.tr;

import java.io.File;
import java.io.IOException;
import java.text.MessageFormat;
import java.util.List;

import javax.swing.JFrame;
import javax.swing.JOptionPane;

import org.openstreetmap.atlas.geography.atlas.Atlas;
import org.openstreetmap.atlas.geography.atlas.AtlasResourceLoader;
import org.openstreetmap.atlas.utilities.collections.Iterables;
import org.openstreetmap.atlas.utilities.scalars.Duration;
import org.openstreetmap.atlas.utilities.time.Time;
import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.ExtensionFileFilter;
import org.openstreetmap.josm.data.Bounds;
import org.openstreetmap.josm.data.osm.DataSet;
import org.openstreetmap.josm.gui.io.importexport.FileImporter;
import org.openstreetmap.josm.gui.progress.ProgressMonitor;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.io.IllegalDataException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author jgage
 */
public class AtlasFileImporter extends FileImporter
{
    private static final Logger logger = LoggerFactory.getLogger(AtlasFileImporter.class);
    private AtlasReaderLayer layer;
    private Atlas atlas;

    public AtlasFileImporter()
    {
        super(new ExtensionFileFilter("atlas,atlas.gz", "atlas", tr("Atlas file") + " (*.atlas"));
    }

    public AtlasReaderLayer getLayer()
    {
        return this.layer;
    }

    @Override
    public void importData(final File file, final ProgressMonitor monitor)
    {
        this.atlas = null;
        final org.openstreetmap.atlas.streaming.resource.File atlasFile = new org.openstreetmap.atlas.streaming.resource.File(
                file.getPath());
        monitor.setCustomText(MessageFormat.format("Parsing Atlas: {}", file.getAbsolutePath()));
        try
        {
            this.atlas = new AtlasResourceLoader().load(atlasFile);

        }
        catch (final Exception e)
        {
            e.printStackTrace();
            JOptionPane.showMessageDialog(null, e.toString(), "Corrupt Atlas File",
                    JOptionPane.ERROR_MESSAGE);
        }
        final AtlasDataSetBuilder builder = new AtlasDataSetBuilder();
        final long start = System.currentTimeMillis();
        final DataSet data = builder.build(this.atlas, monitor);
        final long completedIn = System.currentTimeMillis() - start;
        logger.info("Completed in: {} miliseconds", completedIn);
        final Bounds bounds = builder.getBounds();
        this.layer = new AtlasReaderLayer("Atlas: " + this.atlas.getName(), data, this.atlas,
                bounds);

        GuiHelper.runInEDT(() ->
        {
            Main.getLayerManager().addLayer(this.layer);
            Main.getLayerManager().setActiveLayer(this.layer);
            final JFrame parent = new JFrame();
            final String[] metaData = this.atlas.metaData().toString().split(",");
            JOptionPane.showMessageDialog(parent, metaData, "Atlas MetaData",
                    JOptionPane.INFORMATION_MESSAGE);
            final AtlasReaderDialog dialog = new AtlasReaderDialog(this.layer);
            Main.map.addToggleDialog(dialog);
        });
    }

    @Override
    public void importData(final List<File> files, final ProgressMonitor monitor)
            throws IOException, IllegalDataException
    {
        try
        {
            this.atlas = new AtlasResourceLoader().load(Iterables.stream(files)
                    .map(file -> new org.openstreetmap.atlas.streaming.resource.File(file)));

        }
        catch (final Exception e)
        {
            JOptionPane.showMessageDialog(null, e.toString(), "Corrupt Atlas File",
                    JOptionPane.ERROR_MESSAGE);
        }
        final AtlasDataSetBuilder builder = new AtlasDataSetBuilder();
        final Time start = Time.now();
        final DataSet data = builder.build(this.atlas, monitor);
        final Duration completedIn = start.elapsedSince();
        logger.info("Completed in: {}", completedIn);
        final Bounds bounds = builder.getBounds();
        this.layer = new AtlasReaderLayer("Atlas: " + this.atlas.getName(), data, this.atlas,
                bounds);

        GuiHelper.runInEDT(() ->
        {
            Main.getLayerManager().addLayer(this.layer);
            Main.getLayerManager().setActiveLayer(this.layer);
            final AtlasReaderDialog dialog = new AtlasReaderDialog(this.layer);
            Main.map.addToggleDialog(dialog);
        });
    }

    @Override
    public boolean isBatchImporter()
    {
        return true;
    }

}
