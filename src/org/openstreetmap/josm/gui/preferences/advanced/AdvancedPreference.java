// License: GPL. For details, see LICENSE file.
package org.openstreetmap.josm.gui.preferences.advanced;

import static org.openstreetmap.josm.tools.I18n.marktr;
import static org.openstreetmap.josm.tools.I18n.tr;

import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Objects;

import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPopupMenu;
import javax.swing.JScrollPane;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.MenuEvent;
import javax.swing.event.MenuListener;
import javax.swing.filechooser.FileFilter;

import org.openstreetmap.josm.Main;
import org.openstreetmap.josm.actions.DiskAccessAction;
import org.openstreetmap.josm.data.CustomConfigurator;
import org.openstreetmap.josm.data.Preferences;
import org.openstreetmap.josm.data.Preferences.Setting;
import org.openstreetmap.josm.gui.actionsupport.LogShowDialog;
import org.openstreetmap.josm.gui.preferences.DefaultTabPreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSetting;
import org.openstreetmap.josm.gui.preferences.PreferenceSettingFactory;
import org.openstreetmap.josm.gui.preferences.PreferenceTabbedPane;
import org.openstreetmap.josm.gui.util.GuiHelper;
import org.openstreetmap.josm.gui.widgets.AbstractFileChooser;
import org.openstreetmap.josm.gui.widgets.JosmTextField;
import org.openstreetmap.josm.tools.GBC;

/**
 * Advanced preferences, allowing to set preference entries directly.
 */
public final class AdvancedPreference extends DefaultTabPreferenceSetting {

    /**
     * Factory used to create a new {@code AdvancedPreference}.
     */
    public static class Factory implements PreferenceSettingFactory {
        @Override
        public PreferenceSetting createPreferenceSetting() {
            return new AdvancedPreference();
        }
    }

    private AdvancedPreference() {
        super(/* ICON(preferences/) */ "advanced", tr("Advanced Preferences"), tr("Setting Preference entries directly. Use with caution!"));
    }

    @Override
    public boolean isExpert() {
        return true;
    }

    protected List<PrefEntry> allData;
    protected List<PrefEntry> displayData = new ArrayList<>();
    protected JosmTextField txtFilter;
    protected PreferencesTable table;

    @Override
    public void addGui(final PreferenceTabbedPane gui) {
        JPanel p = gui.createPreferenceTab(this);

        txtFilter = new JosmTextField();
        JLabel lbFilter = new JLabel(tr("Search: "));
        lbFilter.setLabelFor(txtFilter);
        p.add(lbFilter);
        p.add(txtFilter, GBC.eol().fill(GBC.HORIZONTAL));
        txtFilter.getDocument().addDocumentListener(new DocumentListener(){
            @Override public void changedUpdate(DocumentEvent e) {
                action();
            }
            @Override public void insertUpdate(DocumentEvent e) {
                action();
            }
            @Override public void removeUpdate(DocumentEvent e) {
                action();
            }
            private void action() {
                applyFilter();
            }
        });
        readPreferences(Main.pref);

        applyFilter();
        table = new PreferencesTable(displayData);
        JScrollPane scroll = new JScrollPane(table);
        p.add(scroll, GBC.eol().fill(GBC.BOTH));
        scroll.setPreferredSize(new Dimension(400,200));

        JButton add = new JButton(tr("Add"));
        p.add(Box.createHorizontalGlue(), GBC.std().fill(GBC.HORIZONTAL));
        p.add(add, GBC.std().insets(0,5,0,0));
        add.addActionListener(new ActionListener(){
            @Override public void actionPerformed(ActionEvent e) {
                PrefEntry pe = table.addPreference(gui);
                if (pe!=null) {
                    allData.add(pe);
                    Collections.sort(allData);
                    applyFilter();
                }
            }
        });

        JButton edit = new JButton(tr("Edit"));
        p.add(edit, GBC.std().insets(5,5,5,0));
        edit.addActionListener(new ActionListener(){
            @Override public void actionPerformed(ActionEvent e) {
                boolean ok = table.editPreference(gui);
                if (ok) applyFilter();
            }
        });

        JButton reset = new JButton(tr("Reset"));
        p.add(reset, GBC.std().insets(0,5,0,0));
        reset.addActionListener(new ActionListener(){
            @Override public void actionPerformed(ActionEvent e) {
                table.resetPreferences(gui);
            }
        });

        JButton read = new JButton(tr("Read from file"));
        p.add(read, GBC.std().insets(5,5,0,0));
        read.addActionListener(new ActionListener(){
            @Override public void actionPerformed(ActionEvent e) {
                readPreferencesFromXML();
            }
        });

        JButton export = new JButton(tr("Export selected items"));
        p.add(export, GBC.std().insets(5,5,0,0));
        export.addActionListener(new ActionListener(){
            @Override public void actionPerformed(ActionEvent e) {
                exportSelectedToXML();
            }
        });

        final JButton more = new JButton(tr("More..."));
        p.add(more, GBC.std().insets(5,5,0,0));
        more.addActionListener(new ActionListener() {
            JPopupMenu menu = buildPopupMenu();
            @Override public void actionPerformed(ActionEvent ev) {
                menu.show(more, 0, 0);
            }
        });
    }

    private void readPreferences(Preferences tmpPrefs) {
        Map<String, Setting<?>> loaded;
        Map<String, Setting<?>> orig = Main.pref.getAllSettings();
        Map<String, Setting<?>> defaults = tmpPrefs.getAllDefaults();
        orig.remove("osm-server.password");
        defaults.remove("osm-server.password");
        if (tmpPrefs != Main.pref) {
            loaded = tmpPrefs.getAllSettings();
            // plugins preference keys may be changed directly later, after plugins are downloaded
            // so we do not want to show it in the table as "changed" now
            Setting<?> pluginSetting = orig.get("plugins");
            if (pluginSetting!=null) {
                loaded.put("plugins", pluginSetting);
            }
        } else {
            loaded = orig;
        }
        allData = prepareData(loaded, orig, defaults);
    }

    private File[] askUserForCustomSettingsFiles(boolean saveFileFlag, String title) {
        FileFilter filter = new FileFilter() {
            @Override
            public boolean accept(File f) {
                return f.isDirectory() || f.getName().toLowerCase().endsWith(".xml");
            }
            @Override
            public String getDescription() {
                return tr("JOSM custom settings files (*.xml)");
            }
        };
        AbstractFileChooser fc = DiskAccessAction.createAndOpenFileChooser(!saveFileFlag, !saveFileFlag, title, filter,
                JFileChooser.FILES_ONLY, "customsettings.lastDirectory");
        if (fc != null) {
            File[] sel = fc.isMultiSelectionEnabled() ? fc.getSelectedFiles() : (new File[]{fc.getSelectedFile()});
            if (sel.length==1 && !sel[0].getName().contains(".")) sel[0]=new File(sel[0].getAbsolutePath()+".xml");
            return sel;
        }
        return new File[0];
    }

    private void exportSelectedToXML() {
        List<String> keys = new ArrayList<>();
        boolean hasLists = false;

        for (PrefEntry p: table.getSelectedItems()) {
            // preferences with default values are not saved
            if (!(p.getValue() instanceof Preferences.StringSetting)) {
                hasLists = true; // => append and replace differs
            }
            if (!p.isDefault()) {
                keys.add(p.getKey());
            }
        }

        if (keys.isEmpty()) {
            JOptionPane.showMessageDialog(Main.parent,
                    tr("Please select some preference keys not marked as default"), tr("Warning"), JOptionPane.WARNING_MESSAGE);
            return;
        }

        File[] files = askUserForCustomSettingsFiles(true, tr("Export preferences keys to JOSM customization file"));
        if (files.length == 0) {
            return;
        }

        int answer = 0;
        if (hasLists) {
            answer = JOptionPane.showOptionDialog(
                    Main.parent, tr("What to do with preference lists when this file is to be imported?"), tr("Question"),
                    JOptionPane.YES_NO_CANCEL_OPTION, JOptionPane.QUESTION_MESSAGE, null,
                    new String[]{tr("Append preferences from file to existing values"), tr("Replace existing values")}, 0);
        }
        CustomConfigurator.exportPreferencesKeysToFile(files[0].getAbsolutePath(), answer == 0, keys);
    }

    private void readPreferencesFromXML() {
        File[] files = askUserForCustomSettingsFiles(false, tr("Open JOSM customization file"));
        if (files.length == 0) return;

        Preferences tmpPrefs = CustomConfigurator.clonePreferences(Main.pref);

        StringBuilder log = new StringBuilder();
        log.append("<html>");
        for (File f : files) {
            CustomConfigurator.readXML(f, tmpPrefs);
            log.append(CustomConfigurator.getLog());
        }
        log.append("</html>");
        String msg = log.toString().replace("\n", "<br/>");

        new LogShowDialog(tr("Import log"), tr("<html>Here is file import summary. <br/>"
                + "You can reject preferences changes by pressing \"Cancel\" in preferences dialog <br/>"
                + "To activate some changes JOSM restart may be needed.</html>"), msg).showDialog();

        readPreferences(tmpPrefs);
        // sorting after modification - first modified, then non-default, then default entries
        Collections.sort(allData, customComparator);
        applyFilter();
    }

    private Comparator<PrefEntry> customComparator = new Comparator<PrefEntry>() {
        @Override
        public int compare(PrefEntry o1, PrefEntry o2) {
            if (o1.isChanged() && !o2.isChanged()) return -1;
            if (o2.isChanged() && !o1.isChanged()) return 1;
            if (!(o1.isDefault()) && o2.isDefault()) return -1;
            if (!(o2.isDefault()) && o1.isDefault()) return 1;
            return o1.compareTo(o2);
        }
    };

    private List<PrefEntry> prepareData(Map<String, Setting<?>> loaded, Map<String, Setting<?>> orig, Map<String, Setting<?>> defaults) {
        List<PrefEntry> data = new ArrayList<>();
        for (Entry<String, Setting<?>> e : loaded.entrySet()) {
            Setting<?> value = e.getValue();
            Setting<?> old = orig.get(e.getKey());
            Setting<?> def = defaults.get(e.getKey());
            if (def == null) {
                def = value.getNullInstance();
            }
            PrefEntry en = new PrefEntry(e.getKey(), value, def, false);
            // after changes we have nondefault value. Value is changed if is not equal to old value
            if (!Objects.equals(old, value)) {
                en.markAsChanged();
            }
            data.add(en);
        }
        for (Entry<String, Setting<?>> e : defaults.entrySet()) {
            if (!loaded.containsKey(e.getKey())) {
                PrefEntry en = new PrefEntry(e.getKey(), e.getValue(), e.getValue(), true);
                // after changes we have default value. So, value is changed if old value is not default
                Setting<?> old = orig.get(e.getKey());
                if (old != null) {
                    en.markAsChanged();
                }
                data.add(en);
            }
        }
        Collections.sort(data);
        displayData.clear();
        displayData.addAll(data);
        return data;
    }

    Map<String,String> profileTypes = new LinkedHashMap<>();

    private JPopupMenu buildPopupMenu() {
        JPopupMenu menu = new JPopupMenu();
        profileTypes.put(marktr("shortcut"), "shortcut\\..*");
        profileTypes.put(marktr("color"), "color\\..*");
        profileTypes.put(marktr("toolbar"), "toolbar.*");
        profileTypes.put(marktr("imagery"), "imagery.*");

        for (Entry<String,String> e: profileTypes.entrySet()) {
            menu.add(new ExportProfileAction(Main.pref, e.getKey(), e.getValue()));
        }

        menu.addSeparator();
        menu.add(getProfileMenu());
        menu.addSeparator();
        menu.add(new AbstractAction(tr("Reset preferences")) {
            @Override
            public void actionPerformed(ActionEvent ae) {
                if (!GuiHelper.warnUser(tr("Reset preferences"),
                        "<html>"+
                        tr("You are about to clear all preferences to their default values<br />"+
                        "All your settings will be deleted: plugins, imagery, filters, toolbar buttons, keyboard, etc. <br />"+
                        "Are you sure you want to continue?")
                        +"</html>", null, "")) {
                    Main.pref.resetToDefault();
                    try {
                        Main.pref.save();
                    } catch (IOException e) {
                        Main.warn("IOException while saving preferences: "+e.getMessage());
                    }
                    readPreferences(Main.pref);
                    applyFilter();
                }
            }
        });
        return menu;
    }

    private JMenu getProfileMenu() {
        final JMenu p =new JMenu(tr("Load profile"));
        p.addMenuListener(new MenuListener() {
            @Override
            public void menuSelected(MenuEvent me) {
                p.removeAll();
                for (File f: new File(".").listFiles()) {
                   String s = f.getName();
                   int idx = s.indexOf('_');
                   if (idx>=0) {
                        String t=s.substring(0,idx);
                        if (profileTypes.containsKey(t)) {
                            p.add(new ImportProfileAction(s, f, t));
                        }
                   }
                }
                for (File f: Main.pref.getPreferencesDirectory().listFiles()) {
                   String s = f.getName();
                   int idx = s.indexOf('_');
                   if (idx>=0) {
                        String t=s.substring(0,idx);
                        if (profileTypes.containsKey(t)) {
                            p.add(new ImportProfileAction(s, f, t));
                        }
                   }
                }
            }
            @Override public void menuDeselected(MenuEvent me) { }
            @Override public void menuCanceled(MenuEvent me) { }
        });
        return p;
    }

    private class ImportProfileAction extends AbstractAction {
        private final File file;
        private final String type;

        public ImportProfileAction(String name, File file, String type) {
            super(name);
            this.file = file;
            this.type = type;
        }

        @Override
        public void actionPerformed(ActionEvent ae) {
            Preferences tmpPrefs = CustomConfigurator.clonePreferences(Main.pref);
            CustomConfigurator.readXML(file, tmpPrefs);
            readPreferences(tmpPrefs);
            String prefRegex = profileTypes.get(type);
            // clean all the preferences from the chosen group
            for (PrefEntry p : allData) {
               if (p.getKey().matches(prefRegex) && !p.isDefault()) {
                    p.reset();
               }
            }
            // allow user to review the changes in table
            Collections.sort(allData, customComparator);
            applyFilter();
        }
    }

    private void applyFilter() {
        displayData.clear();
        for (PrefEntry e : allData) {
            String prefKey = e.getKey();
            Setting<?> valueSetting = e.getValue();
            String prefValue = valueSetting.getValue() == null ? "" : valueSetting.getValue().toString();

            String[] input = txtFilter.getText().split("\\s+");
            boolean canHas = true;

            // Make 'wmsplugin cache' search for e.g. 'cache.wmsplugin'
            final String prefKeyLower = prefKey.toLowerCase();
            final String prefValueLower = prefValue.toLowerCase();
            for (String bit : input) {
                bit = bit.toLowerCase();
                if (!prefKeyLower.contains(bit) && !prefValueLower.contains(bit)) {
                    canHas = false;
                    break;
                }
            }
            if (canHas) {
                displayData.add(e);
            }
        }
        if (table!=null) table.fireDataChanged();
    }

    @Override
    public boolean ok() {
        for (PrefEntry e : allData) {
            if (e.isChanged()) {
                Main.pref.putSetting(e.getKey(), e.getValue().getValue() == null ? null : e.getValue());
            }
        }
        return false;
    }
}
