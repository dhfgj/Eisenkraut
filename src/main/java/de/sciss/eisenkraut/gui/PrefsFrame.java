/*
 *  PrefsFrame.java
 *  Eisenkraut
 *
 *  Copyright (c) 2004-2016 Hanns Holger Rutz. All rights reserved.
 *
 *  This software is published under the GNU General Public License v3+
 *
 *
 *	For further information, please contact Hanns Holger Rutz at
 *	contact@sciss.de
 */

package de.sciss.eisenkraut.gui;

import de.sciss.app.AbstractApplication;
import de.sciss.app.AbstractWindow;
import de.sciss.app.Application;
import de.sciss.app.PreferenceEntrySync;
import de.sciss.common.AppWindow;
import de.sciss.common.BasicPathField;
import de.sciss.common.BasicWindowHandler;
import de.sciss.eisenkraut.Main;
import de.sciss.eisenkraut.io.AudioBoxConfig;
import de.sciss.eisenkraut.io.PrefCacheManager;
import de.sciss.eisenkraut.math.ConstQPane;
import de.sciss.eisenkraut.net.OSCGUI;
import de.sciss.eisenkraut.net.OSCRoot;
import de.sciss.eisenkraut.util.PrefsUtil;
import de.sciss.gui.ComboBoxEditorBorder;
import de.sciss.gui.CoverGrowBox;
import de.sciss.gui.GUIUtil;
import de.sciss.gui.HelpButton;
import de.sciss.gui.IndeterminateSpinner;
import de.sciss.gui.ModificationButton;
import de.sciss.gui.PathField;
import de.sciss.gui.PrefCheckBox;
import de.sciss.gui.PrefComboBox;
import de.sciss.gui.PrefParamField;
import de.sciss.gui.PrefTextField;
import de.sciss.gui.SortedTableModel;
import de.sciss.gui.SpringPanel;
import de.sciss.gui.StringItem;
import de.sciss.gui.TreeExpanderButton;
import de.sciss.io.IOUtil;
import de.sciss.net.OSCChannel;
import de.sciss.util.Flag;
import de.sciss.util.Param;
import de.sciss.util.ParamSpace;

import javax.swing.*;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.JTableHeader;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 *  This is the frame that
 *  displays the user adjustable
 *  application and session preferences
 */
public class PrefsFrame
        extends AppWindow
        implements SwingConstants {

    private static final ParamSpace spcIntegerFromZero = new ParamSpace(0, Double.POSITIVE_INFINITY, 1, 0, 0, 0);
    private static final ParamSpace spcIntegerFromOne  = new ParamSpace(1, Double.POSITIVE_INFINITY, 1, 0, 0, 1);

    private final Preferences		audioPrefs;
    protected final Preferences		abPrefs;

    // audio box config
    protected final List<AudioBoxConfig> collAudioBoxConfigs	= new ArrayList<AudioBoxConfig>();
    private final Set<String> setAudioBoxIDs		= new HashSet<String>();
    protected final Set<String> setAudioBoxNames	= new HashSet<String>();
    protected static final String[]	audioBoxColNames	= { "prefsAudioDevice", "prefsAudioInputChannels", "prefsAudioOutputChannels", "prefsAudioDeviceActive" };

    private static final StringItem[] RATE_ITEMS = {
            new StringItem(new Param(0, ParamSpace.FREQ | ParamSpace.HERTZ).toString(), "System Default"),
            new StringItem(new Param(44100, ParamSpace.FREQ | ParamSpace.HERTZ).toString(), "44.1 kHz"),
            new StringItem(new Param(48000, ParamSpace.FREQ | ParamSpace.HERTZ).toString(), "48 kHz"),
            new StringItem(new Param(88200, ParamSpace.FREQ | ParamSpace.HERTZ).toString(), "88.2 kHz"),
            new StringItem(new Param(96000, ParamSpace.FREQ | ParamSpace.HERTZ).toString(), "96 kHz")
    };

    protected AudioBoxTableModel	abtm;

    /**
     *  Creates a new preferences frame
     */
    public PrefsFrame() {
        super(SUPPORT);

        setTitle(getResourceString("framePrefs"));

        final Container					cp					= getContentPane();
        final Application				app					= AbstractApplication.getApplication();
        final OSCRoot					osc;
        final PrefCacheManager			cm;
        final Flag						haveWarned          = new Flag(false);
        final String					txtWarnLookAndFeel  = getResourceString("warnLookAndFeelUpdate");

        final TreeExpanderButton		ggTreeAudio;
        final List<JComponent> collAudioAdvanced;
        SpringPanel						tab;
        PrefParamField					ggParam;
        BasicPathField					ggPath;
        PrefCheckBox					ggCheckBox;
        PrefComboBox					ggChoice;
        PrefTextField                   ggTextField;
        JTabbedPane						ggTabPane;
        JLabel							lb;
        JComboBox						ggCombo;
        UIManager.LookAndFeelInfo[]		lafInfo;
        Box								b;

        Preferences						prefs;
        String							key, key2, title;
        int								row;

        ggTabPane	= new JTabbedPane();
        ggTabPane.putClientProperty("styleId", "attached");

        // ---------- global pane ----------

        tab		= createTab();

        row		= 0;
        prefs   = IOUtil.getUserPrefs();
        key		= IOUtil.KEY_TEMPDIR;
        key2	= "prefsTmpDir";
        lb		= new JLabel( getResourceString( key2 ), TRAILING );
        tab.gridAdd( lb, 0, row );
        ggPath	= new BasicPathField( PathField.TYPE_FOLDER, getResourceString( key2 ));
        ggPath.setPreferences( prefs, key );
        tab.gridAdd( ggPath, 1, row );

        row++;
        cm		= PrefCacheManager.getInstance();
        prefs   = cm.getPreferences();
        key		= PrefCacheManager.KEY_ACTIVE;
        key2	= "prefsCache";
        lb		= new JLabel( getResourceString( key2 ), TRAILING );
        tab.gridAdd( lb, 0, row );
        ggCheckBox = new PrefCheckBox( getResourceString( "prefsCacheActive" ));
        ggCheckBox.setPreferences( prefs, key );
//		tab.gridAdd( ggCheckBox, 1, row, -1, 1 );
        b		= Box.createHorizontalBox();
        b.add( ggCheckBox );
//
//		row++;
        key		= PrefCacheManager.KEY_CAPACITY;
        key2	= "prefsCacheCapacity";
        lb		= new JLabel( getResourceString( key2 ) + " [" +
                              getResourceString( "labelMegaBytes" ) + "]", TRAILING );
//		tab.gridAdd( lb, 0, row );
        b.add( Box.createHorizontalStrut( 16 ));
        b.add( lb );
        ggParam  = new PrefParamField();
        ggParam.addSpace( spcIntegerFromOne );
        ggParam.setPreferences( prefs, key );
//		tab.gridAdd( ggParam, 1, row, -1, 1 );
        b.add( ggParam );
        tab.gridAdd( b, 1, row, -1, 1 );

        row++;
        key		= PrefCacheManager.KEY_FOLDER;
        key2	= "prefsCacheFolder";
        lb		= new JLabel( getResourceString( key2 ), TRAILING );
        tab.gridAdd( lb, 0, row );
        ggPath	= new BasicPathField( PathField.TYPE_FOLDER, getResourceString( key2 ));
        ggPath.setPreferences( prefs, key );
        tab.gridAdd( ggPath, 1, row );

        row++;
        prefs   = app.getUserPrefs();
        key		= PrefsUtil.KEY_REVEAL_FILE;
        key2	= "prefsRevealCmd";
        lb		= new JLabel(getResourceString(key2), TRAILING);
        tab.gridAdd(lb, 0, row);
        ggTextField	= new PrefTextField(16);
        ggTextField.setPreferences(prefs, key);
        tab.gridAdd(ggTextField, 1, row);

        row++;
        prefs   = app.getUserPrefs();
        key		= PrefsUtil.KEY_LAF_TYPE;
        title	= "Look-and-Feel";
        ggChoice = new PrefComboBox();
        ggChoice.addItem(new StringItem(PrefsUtil.VALUE_LAF_TYPE_NATIVE      , "Native"));
        ggChoice.addItem(new StringItem(PrefsUtil.VALUE_LAF_TYPE_METAL       , "Metal"));
        ggChoice.addItem(new StringItem(PrefsUtil.VALUE_LAF_TYPE_SUBMIN_LIGHT, "Submin Light"));
        ggChoice.addItem(new StringItem(PrefsUtil.VALUE_LAF_TYPE_SUBMIN_DARK , "Submin Dark"));
        ggChoice.setPreferences(prefs, key);
        tab.gridAdd(ggChoice, 1, row, -1, 1);
        ggChoice.addActionListener(new WarnPrefsChange(ggChoice, ggChoice, haveWarned, txtWarnLookAndFeel, title));

        tab.gridAdd( ggChoice, 1, row, -1, 1 );

        row++;
        key		= BasicWindowHandler.KEY_LAFDECORATION;
        key2	= "prefsLAFDecoration";
        title	= getResourceString( key2 );
        ggCheckBox  = new PrefCheckBox( title );
        ggCheckBox.setPreferences( prefs, key );
        tab.gridAdd( ggCheckBox, 1, row, -1, 1 );
        ggCheckBox.addActionListener(new WarnPrefsChange(ggCheckBox, ggCheckBox, haveWarned, txtWarnLookAndFeel, title));

        row++;
        key		= BasicWindowHandler.KEY_INTERNALFRAMES;
        key2	= "prefsInternalFrames";
        title	= getResourceString( key2 );
        ggCheckBox  = new PrefCheckBox( title );
        ggCheckBox.setPreferences( prefs, key );
        tab.gridAdd( ggCheckBox, 1, row, -1, 1 );
        ggCheckBox.addActionListener( new WarnPrefsChange( ggCheckBox, ggCheckBox, haveWarned, txtWarnLookAndFeel, title ));

        row++;
        key		= CoverGrowBox.KEY_INTRUDINGSIZE;
        key2	= "prefsIntrudingSize";
        ggCheckBox  = new PrefCheckBox( getResourceString( key2 ));
        ggCheckBox.setPreferences( prefs, key );
        tab.gridAdd( ggCheckBox, 1, row, -1, 1 );

        row++;
        key		= BasicWindowHandler.KEY_FLOATINGPALETTES;
        key2	= "prefsFloatingPalettes";
        ggCheckBox  = new PrefCheckBox( getResourceString( key2 ));
        ggCheckBox.setPreferences( prefs, key );
        tab.gridAdd( ggCheckBox, 1, row, -1, 1 );
        ggCheckBox.addActionListener( new WarnPrefsChange( ggCheckBox, ggCheckBox, haveWarned, txtWarnLookAndFeel, title ));

//		row++;
//  	prefs   = GUIUtil.getUserPrefs();
//		key2	= "prefsKeyStrokeHelp";
//		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
//		tab.gridAdd( lb, 0, row );
//		ggKeyStroke = new KeyStrokeTextField();
//		ggKeyStroke.setPreferences( prefs, key );
//		tab.gridAdd( ggKeyStroke, 1, row, -1, 1 );

        addTab( ggTabPane, tab, "prefsGeneral");

        // ---------- audio pane ----------

        prefs   = app.getUserPrefs().node( PrefsUtil.NODE_AUDIO );
        audioPrefs	= prefs;
        abPrefs	= audioPrefs.node( PrefsUtil.NODE_AUDIOBOXES );
        tab		= createTab();

        row		= 0;
        key		= PrefsUtil.KEY_SUPERCOLLIDERAPP;
        key2	= "prefsSuperColliderApp";
        lb		= new JLabel( getResourceString( key2 ), TRAILING );
        tab.gridAdd( lb, 0, row );
        ggPath = new BasicPathField( PathField.TYPE_INPUTFILE, getResourceString( key2 ));
        ggPath.setPreferences( prefs, key );
//		HelpGlassPane.setHelp( ggPath, key2 );
        tab.gridAdd( ggPath, 1, row );

//		row++;
//		key		= PrefsUtil.KEY_SUPERCOLLIDEROSC;
//		key2	= "prefsSuperColliderOSC";
//		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
//		tab.gridAdd( lb, 0, row );
//		ggText  = new PrefTextField( 32 );
//		ggText.setPreferences( prefs, key );
//		tab.gridAdd( ggText, 1, row, -1, 1 );

        row++;
        key		= PrefsUtil.KEY_AUTOBOOT;
        key2	= "prefsAutoBoot";
 //		lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
 //		tab.gridAdd( lb, 0, row );
        ggCheckBox  = new PrefCheckBox( getResourceString( key2 ));
        ggCheckBox.setPreferences( prefs, key );
 //		HelpGlassPane.setHelp( ggCheckBox, key2 );
        tab.gridAdd( ggCheckBox, 1, row, -1, 1 );

        row++;
        key		= PrefsUtil.KEY_AUTOPLAYFROMFINDER;
        key2	= "prefsAutoPlayFromFinder";
        lb		= new JLabel( getResourceString( key2 ), JLabel.TRAILING );
        tab.gridAdd( lb, 0, row );
        ggChoice = new PrefComboBox();
        ggChoice.addItem( new StringItem( PrefsUtil.AUTOPLAYFROMFINDER_NONE, getResourceString( key2 + "." + PrefsUtil.AUTOPLAYFROMFINDER_NONE )));
        ggChoice.addItem( new StringItem( PrefsUtil.AUTOPLAYFROMFINDER_PLAY, getResourceString( key2 + "." + PrefsUtil.AUTOPLAYFROMFINDER_PLAY )));
        ggChoice.addItem( new StringItem( PrefsUtil.AUTOPLAYFROMFINDER_LOOP, getResourceString( key2 + "." + PrefsUtil.AUTOPLAYFROMFINDER_LOOP )));
        ggChoice.setPreferences( prefs, key );
        tab.gridAdd( ggChoice, 1, row, -1, 1 );

        row++;
        lb		= new JLabel( getResourceString( "labelAudioIFs" ), TRAILING );
        tab.gridAdd( lb, 0, row );
        tab.gridAdd( createAudioBoxGUI(), 1, row, 1, -1 );

        row++;
        key		= PrefsUtil.KEY_AUDIORATE;
        key2	= "prefsAudioRate";
        lb		= new JLabel( getResourceString( key2 ), TRAILING );
        tab.gridAdd( lb, 0, row );
        ggParam  = new PrefParamField();
        ggParam.addSpace( ParamSpace.spcFreqHertz );
        ggCombo = new JComboBox();
        for (StringItem RATE_ITEM : RATE_ITEMS) {
            ggCombo.addItem(RATE_ITEM);
        }
        ggParam.setBorder( new ComboBoxEditorBorder() );
        ggCombo.setEditor( ggParam );
        ggCombo.setEditable( true );
        ggParam.setPreferences( prefs, key ); // important to be _afer_ setEditor because otherwise prefs get overwritten!
//		ggCombo.setSelectedIndex( 0 ); // DEFAULT_RATE
//ggParam.setPreferredSize( new Dimension( ggParam.getPreferredSize().width, Math.max( 24, ggParam.getPreferredSize().height )));
//ggCombo.setPreferredSize( new Dimension( ggCombo.getPreferredSize().width, Math.max( 24, ggCombo.getPreferredSize().height )));
        tab.gridAdd( ggCombo, 1, row, -1, 1 );

        row++;
        b  = Box.createHorizontalBox();
        b.add( Box.createHorizontalGlue() );
        ggTreeAudio	= new TreeExpanderButton();
        b.add( ggTreeAudio );
        b.add( new JLabel( getResourceString( "prefsAdvanced" )));
        tab.gridAdd( b, 0, row );

        collAudioAdvanced = new ArrayList<JComponent>();

        row++;
        key2	= "prefsSuperColliderOSC";
        lb		= new JLabel( getResourceString( key2 ), TRAILING );
        lb.setVisible( false );
        collAudioAdvanced.add( lb );
        tab.gridAdd( lb, 0, row );
        key		= PrefsUtil.KEY_SCPROTOCOL;
        key2	= "prefsOSCProtocol";
        lb		= new JLabel( getResourceString( key2 ), TRAILING );
        b		= Box.createHorizontalBox();
        b.add( Box.createHorizontalStrut( 4 ));
        b.add( lb );
        ggChoice = new PrefComboBox();
        ggChoice.addItem(new StringItem(OSCChannel.TCP, "TCP"));
        ggChoice.addItem(new StringItem(OSCChannel.UDP, "UDP"));
        ggChoice.setPreferences(prefs, key);
        b.add(ggChoice);

        key		= PrefsUtil.KEY_SCPORT;
        key2	= "prefsOSCPort";
        lb		= new JLabel( getResourceString( key2 ), TRAILING );
        b.add( Box.createHorizontalStrut( 16 ));
        b.add( lb );
        ggParam  = new PrefParamField();
        ggParam.addSpace( spcIntegerFromZero );
        ggParam.setPreferences( prefs, key );
        b.add( ggParam );
        b.setVisible( false );
        collAudioAdvanced.add( b );
        tab.gridAdd( b, 1, row, -1, 1 );

        row++;
        key		= PrefsUtil.KEY_SCBLOCKSIZE;
        key2	= "prefsSCBlockSize";
        lb		= new JLabel( getResourceString( key2 ), TRAILING );
        lb.setVisible( false );
        collAudioAdvanced.add( lb );
        tab.gridAdd( lb, 0, row );
        ggParam  = new PrefParamField();
        ggParam.addSpace( spcIntegerFromOne );
        ggParam.setPreferences( prefs, key );
        ggParam.setVisible( false );
        collAudioAdvanced.add( ggParam );
        tab.gridAdd( ggParam, 1, row, -1, 1 );

        row++;
        key		= PrefsUtil.KEY_AUDIOBUSSES;
        key2	= "prefsAudioBuses";
        lb		= new JLabel( getResourceString( key2 ), TRAILING );
        lb.setVisible( false );
        collAudioAdvanced.add( lb );
        tab.gridAdd( lb, 0, row );
        ggParam  = new PrefParamField();
        ggParam.addSpace( spcIntegerFromOne );
        ggParam.setPreferences( prefs, key );
        ggParam.setVisible( false );
        collAudioAdvanced.add( ggParam );
        tab.gridAdd( ggParam, 1, row, -1, 1 );

        row++;
        key		= PrefsUtil.KEY_SCMEMSIZE;
        key2	= "prefsSCMemSize";
        lb		= new JLabel( getResourceString( key2 ), TRAILING );
        lb.setVisible( false );
        collAudioAdvanced.add( lb );
        tab.gridAdd( lb, 0, row );
        ggParam  = new PrefParamField();
        ggParam.addSpace( spcIntegerFromOne );
        ggParam.setPreferences( prefs, key );
        ggParam.setVisible( false );
        collAudioAdvanced.add( ggParam );
        tab.gridAdd( ggParam, 1, row, -1, 1 );

        row++;
        key		= PrefsUtil.KEY_SCRENDEZVOUS;
        key2	= "prefsSCRendezvous";
        lb		= new JLabel( getResourceString( key2 ), TRAILING );
        lb.setVisible( false );
        collAudioAdvanced.add( lb );
        tab.gridAdd( lb, 0, row );
        ggCheckBox  = new PrefCheckBox();
        ggCheckBox.setPreferences( prefs, key );
        ggCheckBox.setVisible( false );
        collAudioAdvanced.add( ggCheckBox );
        tab.gridAdd( ggCheckBox, 1, row, -1, 1 );

final SpringPanel tabAudio = tab;
        ggTreeAudio.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent e )
            {
                final int		width	= getWindow().getWidth();
                final int		height	= getWindow().getHeight();
                final boolean	visible	= ggTreeAudio.isExpanded();
                int				delta	= 0;
                int				d2;

                for( int i = 0; i < collAudioAdvanced.size(); ) {
                    d2 = 0;
                    for( int j = i + 2; i < j; i++ ) {
                        d2 = Math.max( d2, collAudioAdvanced.get( i ).getPreferredSize().height );
                    }
                    delta = delta + d2 + 2;
                }

                for (JComponent aCollAudioAdvanced : collAudioAdvanced) {
                    aCollAudioAdvanced.setVisible(visible);
                }
                tabAudio.makeCompactGrid();
                getWindow().setSize( width, height + (visible ? delta : -delta ));
            }
        });

        addTab( ggTabPane, tab, "prefsAudio" );

        // ---------- osc pane ----------

        tab		= createTab();

        row		= 0;
        osc		= OSCRoot.getInstance();
        prefs   = osc.getPreferences();
        key		= OSCRoot.KEY_ACTIVE;
//		key2	= "prefsOSCActive";
        key2	= "prefsOSCServer";
        lb		= new JLabel( getResourceString( key2 ), TRAILING );
        tab.gridAdd( lb, 0, row );
        b		= Box.createHorizontalBox();
        ggCheckBox = new PrefCheckBox( getResourceString( "prefsOSCActive" ));
        ggCheckBox.setPreferences( prefs, key );
//		tab.gridAdd( ggCheckBox, 1, row, -1, 1 );
        b.add( ggCheckBox );

        key		= OSCRoot.KEY_PROTOCOL;
        key2	= "prefsOSCProtocol";
        lb		= new JLabel( getResourceString( key2 ), TRAILING );
//		tab.gridAdd( lb, 2, row );
        b.add( Box.createHorizontalStrut( 16 ));
        b.add( lb );
        ggChoice = new PrefComboBox();
        ggChoice.addItem( new StringItem( OSCChannel.TCP, "TCP" ));
        ggChoice.addItem( new StringItem( OSCChannel.UDP, "UDP" ));
        ggChoice.setPreferences( prefs, key );
//		tab.gridAdd( ggChoice, 3, row, -1, 1 );
        b.add( ggChoice );

        key		= OSCRoot.KEY_PORT;
        key2	= "prefsOSCPort";
        lb		= new JLabel( getResourceString( key2 ), TRAILING );
//		tab.gridAdd( lb, 4, row );
        b.add( Box.createHorizontalStrut( 16 ));
        b.add( lb );
        ggParam  = new PrefParamField();
        ggParam.addSpace( spcIntegerFromZero );
        ggParam.setPreferences( prefs, key );
//		tab.gridAdd( ggParam, 5, row, -1, 1 );
        b.add( ggParam );
        tab.gridAdd( b, 1, row, -1, 1 );

        row++;
        key		= OSCGUI.KEY_SWINGAPP;
        key2	= "prefsOSCSwingApp";
        lb		= new JLabel( getResourceString( key2 ), TRAILING );
        tab.gridAdd( lb, 0, row );
        ggPath = new BasicPathField( PathField.TYPE_INPUTFILE, getResourceString( key2 ));
        ggPath.setPreferences( prefs, key );
        tab.gridAdd( ggPath, 1, row );

        addTab( ggTabPane, tab, "prefsOSC" );

        // ---------- view pane ----------

        prefs   = app.getUserPrefs().node( PrefsUtil.NODE_VIEW );
        tab		= createTab();

        row		= 0;
        key		= PrefsUtil.KEY_SONAENABLED;
//		lb		= new JLabel( "Enable Sonagramme Option (Experimental!)", TRAILING );
//		tab.gridAdd( lb, 0, row );
        ggCheckBox = new PrefCheckBox( "Enable Sonagramme Option (Experimental!)" );
        ggCheckBox.setPreferences( prefs, key );
        tab.gridAdd( ggCheckBox, 0, row, 2, 1 );

        row++;
        tab.gridAdd( new JLabel( " " ), 0, row );

        row++;
        key		= PrefsUtil.NODE_SONAGRAM;
        key2	= "prefsSonoSettings";
        lb		= new JLabel( getResourceString( key2 ), CENTER );
        tab.gridAdd( lb, 0, row, 2, 1 );
        row++;
        final ConstQPane prefConstQ = new ConstQPane();
        prefConstQ.setPreferences( prefs.node( key ));
        tab.gridAdd( prefConstQ, 0, row, -1, 1 );

        addTab( ggTabPane, tab, "prefsView" );

        // ---------- generic gadgets ----------

        cp.add( ggTabPane, BorderLayout.CENTER );
//		AbstractWindowHandler.setDeepFont( cp );

        // ---------- listeners ----------

        addListener( new AbstractWindow.Adapter() {
            public void windowClosing( AbstractWindow.Event e )
            {
                setVisible( false );
                dispose();
            }
        });

        setDefaultCloseOperation( WindowConstants.DO_NOTHING_ON_CLOSE );
        init();
        app.addComponent( Main.COMP_PREFS, this );
    }
    
    private SpringPanel createTab()
    {
        return new SpringPanel( 2, 1, 4, 2 );
    }
    
    private void addTab( JTabbedPane ggTabPane, SpringPanel tab, String key )
    {
        final JPanel tabWrap, p;

        tab.makeCompactGrid();
        tabWrap = new JPanel( new BorderLayout() );
        tabWrap.add( tab, BorderLayout.NORTH );
        p		= new JPanel( new FlowLayout( FlowLayout.RIGHT ));
        p.add( new HelpButton( key ));
        tabWrap.add( p, BorderLayout.SOUTH );
        ggTabPane.addTab( getResourceString( key ), null, tabWrap, null );
    }

    protected boolean autoUpdatePrefs()
    {
        return true;
    }

    public void dispose()
    {
        AbstractApplication.getApplication().removeComponent( Main.COMP_PREFS );
        super.dispose();
    }

    protected static String getResourceString( String key )
    {
        return AbstractApplication.getApplication().getResourceString( key );
    }

    private JComponent createAudioBoxGUI() {
        final JScrollPane			ggScroll;
        final Box					b;
        final ModificationButton	ggPlus, ggMinus;
        final JTable				table;
        final JPanel				p;
        final JTableHeader			th;
        final SortedTableModel		stm;

        audioBoxesFromPrefs();

        p			= new JPanel( new BorderLayout() );

        abtm		= new AudioBoxTableModel();
        stm			= new SortedTableModel( abtm );
        table		= new JTable( stm );
        th			= table.getTableHeader();
        stm.setTableHeader( th );
        th.setReorderingAllowed(false);
        th.setResizingAllowed(true);
        table.setCellSelectionEnabled(true);
        table.setColumnSelectionAllowed(false);
        table.setShowGrid(true);
        table.setSelectionMode(ListSelectionModel.SINGLE_INTERVAL_SELECTION);
        table.setPreferredScrollableViewportSize(new Dimension(256, 64)); // XXX
        for (int i = 0; i < 4; i++) table.getColumnModel().getColumn(i).setPreferredWidth(i == 0 ? 180 : 60);

        stm.setSortedColumn( 0, SortedTableModel.ASCENDING );

        ggScroll	= new JScrollPane( table, ScrollPaneConstants.VERTICAL_SCROLLBAR_ALWAYS,
                                              ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER );

        b			= Box.createHorizontalBox();
        ggPlus		= new ModificationButton( ModificationButton.SHAPE_PLUS );
        ggMinus		= new ModificationButton( ModificationButton.SHAPE_MINUS );
        ggMinus.setEnabled( false );
        ggPlus.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent e )
            {
                final int modelIndex = collAudioBoxConfigs.size();
                final int viewIndex;
                final AudioBoxConfig cfg = createUniqueAudioBox();
                addAudioBox( cfg );
                abtm.fireTableRowsInserted( modelIndex, modelIndex );
                viewIndex = stm.getViewIndex( modelIndex );
                table.setRowSelectionInterval( viewIndex, viewIndex );
                triggerCtrlRoomRefill();
            }
        });
        ggMinus.addActionListener( new ActionListener() {
            public void actionPerformed( ActionEvent e )
            {
                final int firstRow	= Math.max( 0, table.getSelectedRow() );
                final int lastRow	= Math.min( table.getRowCount(), firstRow + table.getSelectedRowCount() ) - 1;
                AudioBoxConfig cfg;
                final int[] modelIndices;

                if( firstRow <= lastRow ) {
                    modelIndices = new int[ lastRow - firstRow + 1 ];
                    for( int i = 0, viewIndex = firstRow; viewIndex <= lastRow; i++, viewIndex++ ) {
                        modelIndices[ i ] = stm.getModelIndex( viewIndex );
                    }
                    Arrays.sort( modelIndices );

                    try {
                        for( int i = modelIndices.length - 1; i >= 0; i-- ) {
                            cfg = collAudioBoxConfigs.get( modelIndices[ i ]);
                            removeAudioBox( cfg );
                        }
                    }
                    catch( BackingStoreException e1 ) {
                        BasicWindowHandler.showErrorDialog( getWindow(), e1, getResourceString( "errSavePrefs" ));
                    } finally {
                        abtm.fireTableDataChanged();
                        triggerCtrlRoomRefill();
                    }
                }
            }
        });
        b.add(ggPlus);
        b.add(ggMinus);
        b.add(Box.createHorizontalGlue());

        table.getSelectionModel().addListSelectionListener(new ListSelectionListener() {
            public void valueChanged(ListSelectionEvent e) {
                ggMinus.setEnabled(table.getSelectedRowCount() > 0);
            }
        });

        p.add(ggScroll, BorderLayout.NORTH);
        p.add(b, BorderLayout.SOUTH);

        p.setBorder(BorderFactory.createEmptyBorder(8, 0, 8, 0));

        return p;
    }

    protected void triggerCtrlRoomRefill()
    {
        final ControlRoomFrame f = (ControlRoomFrame) AbstractApplication.getApplication().getComponent( Main.COMP_CTRLROOM );
        if( f != null ) f.refillAudioBoxes();
    }

    protected void addAudioBox(AudioBoxConfig cfg) {
        collAudioBoxConfigs.add(cfg);
        cfg.toPrefs(abPrefs.node(cfg.id));
        setAudioBoxIDs.add(cfg.id);
        setAudioBoxNames.add(cfg.name);
    }

    protected void removeAudioBox( AudioBoxConfig cfg )
    throws BackingStoreException
    {
        abPrefs.node( cfg.id ).removeNode();
        collAudioBoxConfigs.remove( cfg );
        setAudioBoxIDs.remove( cfg.id );
        setAudioBoxNames.remove( cfg.name );
    }

    private void changeAudioBoxActivity( AudioBoxConfig cfg, boolean newActive )
    throws BackingStoreException
    {
        final int idx;
        final AudioBoxConfig newCfg;

        idx = collAudioBoxConfigs.indexOf( cfg );
        newCfg = cfg.changeActive( newActive );
        newCfg.toPrefs( abPrefs.node( newCfg.id ));
        collAudioBoxConfigs.set( idx, newCfg );
    }

    protected void showAudioBoxAssistant() {
        final String				scsynthPath;
        final Flag					keepThreadRunning	= new Flag( true );
        final Flag					threadRunning		= new Flag( true );
        final JPanel				pane;
        final SpringPanel			pane2;
        final Thread				testThread;
        final Runnable				runResult;
        final IndeterminateSpinner	ggSpinner;
        final JLabel				ggLabel;
        final JList					ggListAdd, ggListActivate, ggListRemove;
        final Box					b;
        final JComboBox				ggComboAdd, ggComboActivate, ggComboRemove;
        final int					result;
        JScrollPane					ggScroll;
//		JSeparator					ggSep;
        int							row, action;
        AudioBoxConfig				cfg;

        final List<AudioBoxConfig> collDetected	= Collections.synchronizedList( new ArrayList<AudioBoxConfig>() );
        final List<AudioBoxConfig> collAdd				= new ArrayList<AudioBoxConfig>();
        final List<AudioBoxConfig> collActivate		= new ArrayList<AudioBoxConfig>();
        final List<AudioBoxConfig> collRemove			= new ArrayList<AudioBoxConfig>();

        // this doesn't show up obviously, so we add it manually...
        collDetected.add( new AudioBoxConfig( AudioBoxConfig.ID_DEFAULT, "Default", 8, 8, true ));

        scsynthPath = audioPrefs.get( PrefsUtil.KEY_SUPERCOLLIDERAPP, null );
        if( scsynthPath == null ) {
            System.err.println( getResourceString( "errSCSynthAppNotFound" ));
            return;
        }

        pane = new JPanel( new BorderLayout() );
//pane.setBackground( Color.white );
        ggSpinner = new IndeterminateSpinner( 24 );
        b = Box.createHorizontalBox();
//		pane.gridAdd( ggSpinner, 0, 0 );
        b.add( ggSpinner );
        b.add( Box.createHorizontalStrut( 4 ));
        ggLabel = new JLabel( "Searching for Audio Devices..." );
//		ggLabel.setBorder( BorderFactory.createEmptyBorder( 0, 4, 0, 0 ));
//		pane.gridAdd( ggLabel, 1, 0 );
        b.add( ggLabel );
        pane.add( b, BorderLayout.NORTH );

        pane2 = new SpringPanel( 0, 0, 4, 2 );
        row = 0;
        ggListAdd = new JList();
        ggScroll = new JScrollPane( ggListAdd );
        pane2.gridAdd( new JLabel( "Detected new devices:" ), 0, row++, 2, 1 );
        pane2.gridAdd( ggScroll, 0, row++, 2, 1 );
        pane2.gridAdd( new JLabel( "Action:", RIGHT ), 0, row );
        ggComboAdd = new JComboBox( new Object[] { "Ignore", "Add to list" });
        pane2.gridAdd( ggComboAdd, 1, row++ );
        pane2.gridAdd( createVSep(), 0, row++, 2, 1 );

        ggListActivate = new JList();
        ggScroll = new JScrollPane( ggListActivate );
        pane2.gridAdd( new JLabel( "Detected deactivated devices:" ), 0, row++, 2, 1 );
        pane2.gridAdd( ggScroll, 0, row++, 2, 1 );
        pane2.gridAdd( new JLabel( "Action:", RIGHT ), 0, row );
        ggComboActivate = new JComboBox( new Object[] { "Ignore", "Make active" });
        pane2.gridAdd( ggComboActivate, 1, row++ );
        pane2.gridAdd( createVSep(), 0, row++, 2, 1 );

//		ggSep	= new JSeparator();
//		ggSep.setBorder( BorderFactory.createEmptyBorder( 8, 0, 8, 0 ));
//		pane2.gridAdd( ggSep, 0, row++, 2, 1 );
        ggListRemove = new JList();
        ggScroll = new JScrollPane( ggListRemove );
        pane2.gridAdd( new JLabel( "Devices not found:" ), 0, row++, 2, 1 );
        pane2.gridAdd( ggScroll, 0, row++, 2, 1 );
        pane2.gridAdd( new JLabel( "Action:", RIGHT ), 0, row );
        ggComboRemove = new JComboBox( new Object[] { "Ignore", "Make inactive", "Remove from list" });
        pane2.gridAdd( ggComboRemove, 1, row);
        // row++;
        pane2.setVisible( false );
        pane.add( pane2, BorderLayout.CENTER );
//		AbstractWindowHandler.setDeepFont( pane );

//		op = new JOptionPane( pane, JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION,
//				new ImageIcon( GUIUtil.class.getResource( "assistent_64x64.png" )));
//		dlg = op.createDialog( getWindow(), getResourceString( "prefsAudioDevicesAssistent" ) );

        runResult = new Runnable() {
            public void run()
            {
//				String			name;
                AudioBoxConfig	cfg3, cfg2;

                for (AudioBoxConfig aCollDetected1 : collDetected) {
                    cfg2 = aCollDetected1;
                    if (!setAudioBoxNames.contains(cfg2.name)) {
                        collAdd.add(cfg2);
                    } else {
                        cfg3 = boxForName(cfg2.name);
                        if ((cfg3 != null) && !cfg3.active) collActivate.add(cfg3);
                    }
                }
iterRemove:
for (AudioBoxConfig collAudioBoxConfig : collAudioBoxConfigs) {
    cfg3 = collAudioBoxConfig;
    for (AudioBoxConfig aCollDetected : collDetected) {
        cfg2 = aCollDetected;
        if (cfg2.name.equals(cfg3.name)) continue iterRemove;
    }
    collRemove.add(cfg3);
}

                Collections.sort(collAdd);
                Collections.sort(collActivate);
                Collections.sort(collRemove);

                ggListAdd.setListData( collAdd.toArray() );
                ggListAdd.setVisibleRowCount( Math.max( 1, Math.min( collAdd.size(), 6 )));
                if( collAdd.isEmpty() ) {
                    ggComboAdd.setEnabled( false );
                } else {
                    ggComboAdd.setSelectedIndex( 1 );
                }
                ggListActivate.setListData( collActivate.toArray() );
                ggListActivate.setVisibleRowCount( Math.max( 1, Math.min( collActivate.size(), 6 )));
                ggListAdd.setVisibleRowCount( Math.max( 1, Math.min( collAdd.size(), 6 )));
                if( collActivate.isEmpty() ) {
                    ggComboActivate.setEnabled( false );
                } else {
                    ggComboActivate.setSelectedIndex( 1 );
                }
                ggListRemove.setListData( collRemove.toArray() );
                ggListRemove.setVisibleRowCount( Math.max( 1, Math.min( collRemove.size(), 6 )));
                if( collRemove.isEmpty() ) {
                    ggComboRemove.setEnabled( false );
                } else {
                    ggComboRemove.setSelectedIndex( 1 );
                }
                pane2.makeCompactGrid();
                b.setVisible( false );
                pane2.setVisible( true );
//				pane.makeCompactGrid();

//				dlg.pack();
                ((JDialog) SwingUtilities.getAncestorOfClass( JDialog.class, pane2 )).pack();
            }
        };

        testThread = new Thread( new Runnable() {
            public void run()
            {
                final InputStream			inStream; // , errStream;

                final byte[]				inBuf		= new byte[128];
//				final byte[]				errBuf		= new byte[128];
                final File					cwd			= new File( scsynthPath ).getParentFile();
                final ByteArrayOutputStream	baos		= new ByteArrayOutputStream();
                final Pattern				ptrn1		= Pattern.compile( "Number of Devices: \\d+\\n(   \\d+ : \".+\" \\(\\d+ ins, \\d+ outs\\)\\n)+\\n" );
//				final Pattern				ptrn2		= Pattern.compile( "\".+\"" );
                final Pattern				ptrn2		= Pattern.compile( "\"(.+)\" \\((\\d+) ins, (\\d+) outs" ); // the trailing () makes the numOuts not disappear (why?!)
                final Matcher				m1			= ptrn1.matcher( "" );
                Matcher						m2;
                String						name;
                Process						p			= null;
                int							len, ins, outs; // , resultCode  	= -1;
                boolean						pRunning	= true;

                try {
                    p			= Runtime.getRuntime().exec( new String[] { scsynthPath, "-u", "0" }, null, cwd );
                    // "Implementation note: It is a good idea for the input stream to be buffered."
                    inStream	= new BufferedInputStream( p.getInputStream() );
//					errStream	= new BufferedInputStream( p.getErrorStream() );

                    while( keepThreadRunning.isSet() && pRunning ) {
                        try {
                            Thread.sleep( 250 );   // a kind of cheesy way to wait for the program to end
                        }
                        catch( InterruptedException e5 ) { /* ignore */ }

                        if( inStream.available() > 0 ) {
                            try {
                                while (inStream.available() > 0) {
                                    len = Math.min(inBuf.length, inStream.available());
                                    final int len1 = inStream.read(inBuf, 0, len);
                                    baos.write(inBuf, 0, len1);
                                }
                            }
                            catch( IOException e1 ) { /* ignore for now XXX */ }

                            m1.reset( baos.toString() );
                            if( m1.find() ) {
                                pRunning = false;
//								keepThreadRunning.set( false );
                                m2 = ptrn2.matcher( m1.group() );
                                while( m2.find() ) {
//									s = m2.group();
//									System.out.println( "'" + s.substring( 1, s.length() - 1 ) + "'" );
//									collDetected.add( s.substring( 1, s.length() - 1 ));
                                    if( m2.groupCount() == 4 ) {
                                        name	= m2.group( 1 );
                                        ins		= Integer.parseInt( m2.group( 2 ));
                                        outs	= Integer.parseInt( m2.group( 3 ));
                                        if( (ins > 0) && (outs > 0) ) { // filter out single direction devices (like mac os x built-in mic and output)
                                            collDetected.add( new AudioBoxConfig(
                                                    null, name, ins, outs, true ));
                                        }
                                    }
                                }
                            }
                        }

//						handleConsole( errStream, errBuf );
                        try {
//							resultCode	=
                                p.exitValue();
                            pRunning	= false;
                            p			= null;
//							printStream.println( "scsynth terminated (" + resultCode +")" );
                        }
                        // gets thrown if we call exitValue() while sc still running
                        catch( IllegalThreadStateException e1 ) { /* ignore */ }
                    } // while( keepThreadRunning && pRunning )
                }
                catch( IOException e3 ) {
                    // XXX
                    System.err.println( "BootThread.run " + e3 );
                }
                finally {
                    if( p != null ) {
//						System.err.println( "scsynth didn't quit. we're killing it!" );
                        p.destroy();
                    }

                    // not when user has cancelled thread!
                    if( keepThreadRunning.isSet() ) EventQueue.invokeLater( runResult );

                    synchronized( threadRunning ) {
                        threadRunning.set( false );
                        threadRunning.notifyAll();
                    }
                }
            }
        });

        testThread.start();
        ggSpinner.setActive( true );

        final JOptionPane op = new JOptionPane( pane,
                                                JOptionPane.PLAIN_MESSAGE, JOptionPane.OK_CANCEL_OPTION,
                                                new ImageIcon( GUIUtil.class.getResource( "assistent_64x64.png" )));
        result = BasicWindowHandler.showDialog( op, getWindow(), getResourceString("prefsAudioDevicesAssistant"));

        synchronized( threadRunning ) {
            if( threadRunning.isSet() ) {  // i.e. premature dialog cancel
                keepThreadRunning.set( false );
                try {
                    threadRunning.wait();
                }
                catch( InterruptedException e1 ) { /* ignore */ }
                return;
            }
        }

        if( result == JOptionPane.OK_OPTION ) {
            try {
                action = ggComboAdd.getSelectedIndex();
                if( action > 0 ) {
                    for (AudioBoxConfig aCollAdd : collAdd) {
                        cfg = aCollAdd;
                        cfg = cfg.changeID(createUniqueAudioBoxID());
                        switch (action) {
                            case 1:
                                addAudioBox(cfg);
                                break;
                            default:
                                assert false : action;
                                break;
                        }
                    }
                }
                action = ggComboActivate.getSelectedIndex();
                if( action > 0 ) {
                    for (AudioBoxConfig aCollActivate : collActivate) {
                        cfg = aCollActivate;
                        switch (action) {
                            case 1:
                                changeAudioBoxActivity(cfg, true);
                                break;
                            default:
                                assert false : action;
                                break;
                        }
                    }
                }
                action = ggComboRemove.getSelectedIndex();
                if( action > 0 ) {
                    for (AudioBoxConfig aCollRemove : collRemove) {
                        cfg = aCollRemove;
                        switch (action) {
                            case 1:
                                changeAudioBoxActivity(cfg, false);
                                break;
                            case 2:
                                removeAudioBox(cfg);
                                break;
                            default:
                                assert false : action;
                                break;
                        }
                    }
                }
            } catch( BackingStoreException e1 ) {
                BasicWindowHandler.showErrorDialog( getWindow(), e1, getResourceString( "errSavePrefs" ));
            } finally {
                abtm.fireTableDataChanged();
                triggerCtrlRoomRefill();
            }
        }
    }

    private JComponent createVSep()
    {
        final Box b = Box.createVerticalBox();
        b.add( Box.createVerticalStrut( 8 ));
        b.add( new JSeparator() );
        b.add( Box.createVerticalStrut( 8 ));
        return b;
    }

    protected AudioBoxConfig boxForName(String name) {
        AudioBoxConfig cfg;
        for (AudioBoxConfig collAudioBoxConfig : collAudioBoxConfigs) {
            cfg = collAudioBoxConfig;
            if (cfg.name.equals(name)) return cfg;
        }
        return null;
    }

    private String createUniqueAudioBoxID() {
        String id = "user1";
        for (int i = 2; setAudioBoxIDs.contains(id); i++) {
            id = "user" + i;
        }
        return id;
    }

    protected AudioBoxConfig createUniqueAudioBox() {
        final String test = getResourceString("labelUntitled");
        String name = test;
        for (int i = 1; setAudioBoxNames.contains(name); i++) {
            name = test + " " + i;
        }

        return new AudioBoxConfig(createUniqueAudioBoxID(), name);
    }

    private void audioBoxesFromPrefs() {
        collAudioBoxConfigs.clear();
        setAudioBoxIDs.clear();
        setAudioBoxNames.clear();

        final String[]		arrayNames;
        AudioBoxConfig		cfg;
        Preferences			cfgPrefs;

        try {
            arrayNames = abPrefs.childrenNames();
        }
        catch( BackingStoreException e1 ) {
            BasicWindowHandler.showErrorDialog( getWindow(), e1, getResourceString( "errLoadPrefs" ));
            return;
        }

        for (String arrayName : arrayNames) {
            cfgPrefs = abPrefs.node(arrayName);
            try {
                cfg = new AudioBoxConfig(cfgPrefs);
                collAudioBoxConfigs.add(cfg);
                setAudioBoxIDs.add(arrayName);
                setAudioBoxNames.add(cfg.name);
            } catch (NumberFormatException e1) {
                System.err.println("audioBoxesFromPrefs:");
                e1.printStackTrace();
            }
        }
    }

    private static class WarnPrefsChange
            implements ActionListener {

        private final PreferenceEntrySync pes;
        private final Component c;
        private final Flag      haveWarned;
        private final String    text;
        private final String    title;
        private final String    initialValue;

        protected WarnPrefsChange(PreferenceEntrySync pes, Component c, Flag haveWarned, String text, String title) {
            this.pes        = pes;
            this.c          = c;
            this.haveWarned = haveWarned;
            this.text       = text;
            this.title      = title;

            initialValue    = pes.getPreferenceNode().get(pes.getPreferenceKey(), null);
        }

        public void actionPerformed(ActionEvent e) {
            final String newValue = pes.getPreferenceNode().get(pes.getPreferenceKey(), initialValue);
            final boolean different = (newValue != null || initialValue != null) &&
                    (newValue == null || !newValue.equals(initialValue));

            if (different && !haveWarned.isSet()) {
                EventQueue.invokeLater ( new Runnable() {
                    public void run ()
                    {
                        JOptionPane.showMessageDialog(c, text, title, JOptionPane.INFORMATION_MESSAGE);
                    }
                });
                haveWarned.set(true);
            }
        }
    }

    @SuppressWarnings("serial")
    private class AudioBoxTableModel
    extends AbstractTableModel
    {
        protected AudioBoxTableModel() { /* empty */ }

        public String getColumnName( int col )
        {
            if( col < audioBoxColNames.length ) {
                return getResourceString( audioBoxColNames[ col ]);
            } else {
                return null;
            }
        }

        public int getRowCount() {
            return collAudioBoxConfigs.size();
        }

        public int getColumnCount() {
            return 4;
        }

        public Object getValueAt(int row, int col) {
            if (row > collAudioBoxConfigs.size()) return null;

            final AudioBoxConfig c = collAudioBoxConfigs.get(row);

            switch (col) {
                case 0:
                    return c.name;
                case 1:
                    return c.numInputChannels;
                case 2:
                    return c.numOutputChannels;
                case 3:
                    return c.active;
                default:
                    return null;
            }
        }

        public Class<?> getColumnClass(int col) {
            switch (col) {
                case 0:
                    return String.class;
                case 1:
                case 2:
                    return Integer.class;
                case 3:
                    return Boolean.class;
                default:
                    return null;
            }
        }

        public boolean isCellEditable( int row, int col )
        {
            return true;
        }

        public void setValueAt( Object value, int row, int col )
        {
            if( (row > collAudioBoxConfigs.size()) || (value == null) ) return;

            final AudioBoxConfig cfg			= collAudioBoxConfigs.get( row );
            String				name;
            AudioBoxConfig		newCfg			= null;
            int					newChannels;
            boolean				newActive;
            boolean				trigger			= false;

            switch( col ) {
            case 0:
                name = value.toString();
                if ((name.length() > 0) &&
                        !setAudioBoxNames.contains(name)) {

                    newCfg = cfg.changeName(name);
                    trigger = true;
                }
                break;

            case 1:
            case 2:
                if( value instanceof Number ) {
                    newChannels = Math.max( 0, ((Number) value).intValue() );
                } else if( value instanceof String ) {
                    try {
                        newChannels = Math.max( 0, Integer.parseInt( value.toString() ));
                    }
                    catch( NumberFormatException e1 ) {
                        break;
                    }
                } else {
                    assert false : value;
                    break;
                }
                if( col == 1 ) {
                    if( newChannels == cfg.numInputChannels ) return;
                    newCfg = cfg.changeChannels( newChannels, cfg.numOutputChannels );
                } else {
                    if( newChannels == cfg.numOutputChannels ) return;
                    newCfg = cfg.changeChannels( cfg.numInputChannels, newChannels );
                }
                break;

            case 3:
                if( value instanceof Boolean ) {
                    newActive = (Boolean) value;
                } else {
                    assert false : value;
                    break;
                }
                if( newActive == cfg.active ) return;
                newCfg = cfg.changeActive( newActive );
                trigger = true;
                break;

            default:
                break;
            }

            if( newCfg != null ) {
                collAudioBoxConfigs.set( row, newCfg );
                if( !cfg.name.equals( newCfg.name )) {
//					try {
//						abPrefs.node( cfg.id ).removeNode();
                        setAudioBoxNames.remove( cfg.name );
                        setAudioBoxNames.add( newCfg.name );
//					}
//					catch( BackingStoreException e1 ) {
//						newCfg = cfg;
//						GUIUtil.displayError( null, e1, getResourceString( "errLoadPrefs" ));
//					}
                }
                newCfg.toPrefs( abPrefs.node( newCfg.id ));
                if( trigger ) triggerCtrlRoomRefill();
            }

            fireTableRowsUpdated( row, row );	// updates sorting!
        }
    }
}