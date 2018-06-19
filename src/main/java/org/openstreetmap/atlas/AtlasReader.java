package org.openstreetmap.atlas;

import org.openstreetmap.josm.actions.ExtensionFileFilter;
import org.openstreetmap.josm.plugins.Plugin;
import org.openstreetmap.josm.plugins.PluginInformation;

/**
 * @author jgage
 */
public class AtlasReader extends Plugin
{
    private final AtlasFileImporter atlasFileImporter;

    public AtlasReader(final PluginInformation info)
    {
        super(info);
        this.atlasFileImporter = new AtlasFileImporter();
        ExtensionFileFilter.addImporter(this.atlasFileImporter);
        ExtensionFileFilter.updateAllFormatsImporter();

    }
}
