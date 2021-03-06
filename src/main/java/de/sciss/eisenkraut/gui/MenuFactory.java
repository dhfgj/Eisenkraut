/*
 *  MenuFactory.java
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
import de.sciss.app.DynamicPrefChangeManager;
import de.sciss.common.BasicApplication;
import de.sciss.common.BasicMenuFactory;
import de.sciss.common.BasicWindowHandler;
import de.sciss.common.ProcessingThread;
import de.sciss.eisenkraut.Main;
import de.sciss.eisenkraut.io.AudioStake;
import de.sciss.eisenkraut.io.PrefCacheManager;
import de.sciss.eisenkraut.net.SuperColliderClient;
import de.sciss.eisenkraut.session.Session;
import de.sciss.eisenkraut.util.PrefsUtil;
import de.sciss.gui.AboutBox;
import de.sciss.gui.BooleanPrefsMenuAction;
import de.sciss.gui.IntPrefsMenuAction;
import de.sciss.gui.MenuAction;
import de.sciss.gui.MenuCheckItem;
import de.sciss.gui.MenuGroup;
import de.sciss.gui.MenuItem;
import de.sciss.gui.MenuRadioGroup;
import de.sciss.gui.MenuRadioItem;
import de.sciss.gui.MenuSeparator;
import de.sciss.io.AudioFileDescr;
import de.sciss.io.AudioFileFormatPane;
import de.sciss.io.Span;
import de.sciss.jcollider.Server;
import de.sciss.util.Flag;
import de.sciss.util.Param;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.StringTokenizer;
import java.util.prefs.PreferenceChangeEvent;
import java.util.prefs.PreferenceChangeListener;
import java.util.prefs.Preferences;

/**
 *  <code>JMenu</code>s cannot be added to more than
 *  one frame. Since on MacOS there's one
 *  global menu for all the application windows
 *  we need to 'duplicate' a menu prototype.
 *  Synchronizing all menus is accomplished
 *  by using the same action objects for all
 *  menu copies. However when items are added
 *  or removed, synchronization needs to be
 *  performed manually. That's the point about
 *  this class.
 *  <p>
 *  There can be only one instance of <code>MenuFactory</code>
 *  for the application, and that will be created by the
 *  <code>Main</code> class.
 */
public class MenuFactory
        extends BasicMenuFactory {

    // ---- misc actions ----
    private ActionOpen				actionOpen;
    private ActionOpenMM			actionOpenMM;
    private ActionNewEmpty			actionNewEmpty;

    /** Base directory of FScape installation.
     * I.e. parent directory of `help`, `sounds` etc.
     */
    private static final File installDir;

    static {
        File base = null;
        final String cp = System.getProperty("java.class.path");
        final String[] paths = cp.split(":");
        for (int i = 0; i < paths.length; i++) {
            final String entry = paths[i];
            if (entry.contains("eisenkraut")) {
                final File lib = new File(entry).getParentFile();
                if (lib != null) {
                    base = lib.getParentFile();
                }
            }
        }
        installDir = (base != null) ? base : new File("").getAbsoluteFile();
    }

    /**
     *  The constructor is called only once by
     *  the <code>Main</code> class and will create a prototype
     *  main menu from which all copies are
     *  derived.
     */
    public MenuFactory(BasicApplication app) {
        super(app);

        createActions();
    }

    public ProcessingThread closeAll(boolean force, Flag confirmed) {
        final de.sciss.app.DocumentHandler	dh	= AbstractApplication.getApplication().getDocumentHandler();
        Session								doc;
        ProcessingThread					pt;

        while( dh.getDocumentCount() > 0 ) {
            doc	= (Session) dh.getDocument( 0 );
if( doc.getFrame() == null ) {
    System.err.println( "Warning, no doc frame for "+doc.getDisplayDescr().file );
    try {
        Thread.sleep( 4000 );
    } catch( InterruptedException e1 ) { /* ignore */ }
    confirmed.set( true );
    return null;
}
            pt	= doc.getFrame().closeDocument( force, confirmed );
            if( pt == null ) {
                if( !confirmed.isSet() ) return null;
            } else {
                return pt;
            }
        }
        confirmed.set( true );
        return null;
    }

    private void createActions() {
        // --- file menu ---
        actionNewEmpty = new ActionNewEmpty(getResourceString("menuNewEmpty"),
                KeyStroke.getKeyStroke(KeyEvent.VK_N, MENU_SHORTCUT));
        actionOpen = new ActionOpen(getResourceString("menuOpen"),
                KeyStroke.getKeyStroke(KeyEvent.VK_O, MENU_SHORTCUT));
        actionOpenMM = new ActionOpenMM(getResourceString("menuOpenMM"),
                KeyStroke.getKeyStroke(KeyEvent.VK_O, MENU_SHORTCUT + InputEvent.SHIFT_MASK));
    }

    // todo: this should eventually read the tree from an xml file
    protected void addMenuItems() {
        final Preferences		prefs = getApplication().getUserPrefs();
        MenuGroup				mg, smg;
        MenuCheckItem			mci;
        MenuRadioGroup			rg;
//		Action					a;
        BooleanPrefsMenuAction	ba;
        IntPrefsMenuAction		ia;
        int						i;

        // Ctrl on Mac / Ctrl+Alt on PC
        final int myCtrl = MENU_SHORTCUT == InputEvent.CTRL_MASK ? InputEvent.CTRL_MASK | InputEvent.ALT_MASK : InputEvent.CTRL_MASK;

        // --- file menu ---

        mg	= (MenuGroup) get( "file" );
        smg = new MenuGroup( "new", getResourceString( "menuNew" ));
        smg.add( new MenuItem( "empty", actionNewEmpty ));
        smg.add( new MenuItem( "fromSelection", getResourceString( "menuNewFromSelection" )));
        mg.add( smg, 0 );
        i	= mg.indexOf( "open" );
        mg.add( new MenuItem( "openMultipleMono", actionOpenMM ), i + 1 );
        i	= mg.indexOf( "closeAll" );
        mg.add( new MenuSeparator(), i + 3 );
        i = mg.indexOf( "saveCopyAs" );
        mg.add( new MenuItem( "saveSelectionAs", getResourceString( "menuSaveSelectionAs" )), i + 1 );

        // --- timeline menu ---
        i	= indexOf( "edit" );
        mg	= new MenuGroup( "timeline", getResourceString( "menuTimeline" ));
        mg.add( new MenuItem( "trimToSelection", getResourceString( "menuTrimToSelection" ),
                              KeyStroke.getKeyStroke( KeyEvent.VK_F5, MENU_SHORTCUT )));

        mg.add( new MenuItem( "insertSilence", getResourceString( "menuInsertSilence" ),
                              KeyStroke.getKeyStroke( KeyEvent.VK_E, MENU_SHORTCUT + InputEvent.SHIFT_MASK )));
        mg.add( new MenuItem( "insertRecording", getResourceString( "menuInsertRec" )));
        add( mg, i + 1 );

        // --- process menu ---
        mg = new MenuGroup("process", getResourceString("menuProcess"));
        mg.add(new MenuItem("again", getResourceString("menuProcessAgain"), KeyStroke.getKeyStroke(KeyEvent.VK_F, MENU_SHORTCUT)));
        mg.addSeparator();
        smg = new MenuGroup("fscape", getResourceString("menuFScape"));
        smg.add(new MenuItem("needlehole", getResourceString("menuFScNeedlehole")));
        mg.add(smg);
        smg = new MenuGroup("sc", getResourceString("menuSuperCollider"));
        mg.add(smg);
        mg.addSeparator();
        mg.add(new MenuItem("fadeIn"  , getResourceString("menuFadeIn"), KeyStroke.getKeyStroke(KeyEvent.VK_I, myCtrl)));
        mg.add(new MenuItem("fadeInD" , getResourceString("menuFadeInD")));
        mg.add(new MenuItem("fadeOut" , getResourceString("menuFadeOut"), KeyStroke.getKeyStroke(KeyEvent.VK_O, myCtrl)));
        mg.add(new MenuItem("fadeOutD", getResourceString("menuFadeOutD")));
        mg.add(new MenuItem("gain", getResourceString("menuGain"), KeyStroke.getKeyStroke(KeyEvent.VK_N, myCtrl)));
        mg.add(new MenuItem("invert", getResourceString("menuInvert")));
//		mg.add( new MenuItem( "mix", getResourceString( "menuMix" )));
        mg.add(new MenuItem("reverse", getResourceString("menuReverse")));
        mg.add(new MenuItem("rotateChannels", getResourceString("menuRotateChannels")));
        add(mg, i + 2);

        // --- operation menu ---
        mg			= new MenuGroup( "operation", getResourceString( "menuOperation" ));
        ba			= new BooleanPrefsMenuAction( getResourceString( "menuInsertionFollowsPlay" ), null );
        mci			= new MenuCheckItem( "insertionFollowsPlay", ba );
        ba.setCheckItem( mci );
        ba.setPreferences( prefs, PrefsUtil.KEY_INSERTIONFOLLOWSPLAY );
        mg.add( mci );
        add( mg, i + 3 );

        // --- view menu ---
        mg			= new MenuGroup( "view", getResourceString( "menuView" ));
        smg			= new MenuGroup( "timeUnits", getResourceString( "menuTimeUnits" ));
        ia			= new IntPrefsMenuAction( getResourceString( "menuTimeUnitsSamples" ), null, PrefsUtil.TIME_SAMPLES );
        rg			= new MenuRadioGroup();
        smg.add( new MenuRadioItem( rg, "samples", ia ));	// crucial reihenfolge : erst item erzeugen, dann gruppe setzen, dann prefs
        ia.setRadioGroup( rg );
        ia.setPreferences( prefs, PrefsUtil.KEY_TIMEUNITS );
        ia			= new IntPrefsMenuAction( getResourceString( "menuTimeUnitsMinSecs" ), null, PrefsUtil.TIME_MINSECS );
        smg.add( new MenuRadioItem( rg, "minSecs", ia ));
        ia.setRadioGroup( rg );
        ia.setPreferences( prefs, PrefsUtil.KEY_TIMEUNITS );
        mg.add( smg );

        smg			= new MenuGroup( "vertscale", getResourceString( "menuVertScale" ));
        ia			= new IntPrefsMenuAction( getResourceString( "menuVertScaleAmpLin" ), null, PrefsUtil.VSCALE_AMP_LIN );
        rg			= new MenuRadioGroup();
        smg.add( new MenuRadioItem( rg, "amplin", ia ));	// crucial reihenfolge : erst item erzeugen, dann gruppe setzen, dann prefs
        ia.setRadioGroup( rg );
        ia.setPreferences( prefs, PrefsUtil.KEY_VERTSCALE );
        ia			= new IntPrefsMenuAction( getResourceString( "menuVertScaleAmpLog" ), null, PrefsUtil.VSCALE_AMP_LOG );
        smg.add( new MenuRadioItem( rg, "amplog", ia ));
        ia.setRadioGroup( rg );
        ia.setPreferences( prefs, PrefsUtil.KEY_VERTSCALE );
        ia			= new IntPrefsMenuAction( getResourceString( "menuVertScaleFreqSpect" ), null, PrefsUtil.VSCALE_FREQ_SPECT );
        smg.add( new MenuRadioItem( rg, "freqspect", ia ));
        ia.setRadioGroup( rg );
        ia.setPreferences( prefs, PrefsUtil.KEY_VERTSCALE );
        final IntPrefsMenuAction freqSpectAction = ia;
//		ia.setEnabled( prefs.node( PrefsUtil.NODE_VIEW ).getBoolean( PrefsUtil.KEY_SONAENABLED, false ));
        new DynamicPrefChangeManager( prefs.node( PrefsUtil.NODE_VIEW ), new String[] {
            PrefsUtil.KEY_SONAENABLED }, new PreferenceChangeListener() {
            public void preferenceChange( PreferenceChangeEvent pce )
            {
                freqSpectAction.setEnabled( prefs.node( PrefsUtil.NODE_VIEW ).getBoolean( PrefsUtil.KEY_SONAENABLED, false ));
            }
        }).startListening();

        mg.add( smg );

        ba			= new BooleanPrefsMenuAction( getResourceString( "menuViewNullLinie" ), null );
        mci			= new MenuCheckItem( "nullLinie", ba );
        ba.setCheckItem( mci );
        ba.setPreferences( prefs, PrefsUtil.KEY_VIEWNULLLINIE );
        mg.add( mci );
        ba			= new BooleanPrefsMenuAction( getResourceString( "menuViewVerticalRulers" ), null );
        mci			= new MenuCheckItem( "verticalRulers", ba );
        ba.setCheckItem( mci );
        ba.setPreferences( prefs, PrefsUtil.KEY_VIEWVERTICALRULERS );
        mg.add( mci );
        ba			= new BooleanPrefsMenuAction( getResourceString( "menuViewChanMeters" ), null );
        mci			= new MenuCheckItem( "channelMeters", ba );
        ba.setCheckItem( mci );
        ba.setPreferences( prefs, PrefsUtil.KEY_VIEWCHANMETERS );
        mg.add( mci );
        ba			= new BooleanPrefsMenuAction( getResourceString( "menuViewMarkers" ), null );
        mci			= new MenuCheckItem( "markers", ba );
        ba.setCheckItem( mci );
        ba.setPreferences( prefs, PrefsUtil.KEY_VIEWMARKERS );
        mg.add( mci );
        add( mg, i + 4 );

        // --- window menu ---
//		mWindowRadioGroup = new MenuRadioGroup();
//		mgWindow = new MenuGroup( "window", getResourceString( "menuWindow" ));
        mg	= (MenuGroup) get( "window" );
        mg.add( new MenuItem( "ioSetup", new ActionIOSetup( getResourceString( "frameIOSetup" ), null )), 0 );
        mg.add( new MenuSeparator(), 1 );
        mg.add( new MenuItem( "main", new ActionShowWindow( getResourceString( "frameMain" ), null, Main.COMP_MAIN )), 2 );
        mg.add( new MenuItem( "observer", new ActionObserver( getResourceString( "paletteObserver" ), KeyStroke.getKeyStroke( KeyEvent.VK_3, MENU_SHORTCUT ))), 3 );
        mg.add( new MenuItem( "ctrlRoom", new ActionCtrlRoom( getResourceString( "paletteCtrlRoom" ), KeyStroke.getKeyStroke( KeyEvent.VK_2, MENU_SHORTCUT ))), 4 );
//		mg.add( new MenuSeparator(), 5 );
//		mgWindow.add( new MenuItem( "collect", ((WindowHandler) root.getWindowHandler()).getCollectAction() ));
//		mgWindow.addSeparator();
//		add( mgWindow );

        // --- debug menu ---
        mg   = new MenuGroup( "debug", "Debug" );
        mg.add( new MenuItem( "dumpPrefs", PrefsUtil.getDebugDumpAction() ));
        mg.add( new MenuItem( "dumpRegions", "Dump Region Structure" ));
        mg.add( new MenuItem( "verifyRegions", "Verify Regions Consistency" ));
        mg.add( new MenuItem( "dumpCache", PrefCacheManager.getInstance().getDebugDumpAction() ));
        mg.add( new MenuItem( "dumpAudioStakes", AudioStake.getDebugDumpAction() ));
        mg.add( new MenuItem( "dumpNodeTree", SuperColliderClient.getInstance().getDebugNodeTreeAction() ));
        mg.add( new MenuItem( "dumpKillAll", SuperColliderClient.getInstance().getDebugKillAllAction() ));
        i	= indexOf( "help" );
//		add( mg, i );

        remove(i);

        add(mg);

        mg	= new MenuGroup( "help", getResourceString( "menuHelp" ));
        // this is pretty weird, but it works at least on german keyboards: command+questionmark is defaut help shortcut
        // on mac os x. KeyEvent.VK_QUESTION_MARK doesn't exist, plus apple's vm ignore german keyboard layout, therefore the
        // the question mark becomes a minus. however it's wrongly displayed in the menu...
        try {
            final URL urlManual = new File(new File(installDir, "help"), "index.html").toURI().toURL();
            mg.add(new MenuItem("manual", new URLViewerAction(getResourceString("menuHelpManual"),
                    KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, MENU_SHORTCUT + InputEvent.SHIFT_MASK), urlManual /* "index" */, false)));
        } catch (MalformedURLException e) {
            // nada
        }
        try {
            final URL urlShortcuts = new File(new File(installDir, "help"), "Shortcuts.html").toURI().toURL();
            mg.add(new MenuItem("shortcuts", new URLViewerAction(getResourceString("menuHelpShortcuts"), null, urlShortcuts, false)));
        } catch (MalformedURLException e) {
            // nada
        }
        try {
            final URL urlApp = new URL("http://www.sciss.de/eisenkraut");
            mg.addSeparator();
            mg.add(new MenuItem("website", new URLViewerAction(getResourceString("menuHelpWebsite"), null, urlApp, true)));
        } catch (MalformedURLException e) {
            // nada
        }
        final Action a = new ActionAbout(getResourceString("menuAbout"), null);
//		if (AboutJMenuItem.isAutomaticallyPresent()) {
//			getApplication().getAboutJMenuItem().setAction(a);
//		} else {
            mg.addSeparator();
            mg.add(new MenuItem("about", a));
//		}

        add(mg);

//		// --- help menu ---
//		mg	= new MenuGroup( "help", getResourceString( "menuHelp" ));
//		mg.add( new MenuItem( "manual", new actionURLViewerClass( getResourceString( "menuHelpManual" ), null, "index", false )));
//		mg.add( new MenuItem( "shortcuts", new actionURLViewerClass( getResourceString( "menuHelpShortcuts" ), null, "Shortcuts", false )));
//		mg.addSeparator();
//		mg.add( new MenuItem( "website", new actionURLViewerClass( getResourceString( "menuHelpWebsite" ), null, getResourceString( "appURL" ), true )));
//		a = new actionAboutClass( getResourceString( "menuAbout" ), null );
//		if( AboutJMenuItem.isAutomaticallyPresent() ) {
//			root.getAboutJMenuItem().setAction( a );
//		} else {
//			mg.addSeparator();
//			mg.add( new MenuItem( "about", a ));
//		}
//
//		add( mg );
    }

    public void showPreferences()
    {
        PrefsFrame prefsFrame = (PrefsFrame) getApplication().getComponent( Main.COMP_PREFS );

        if( prefsFrame == null ) {
            prefsFrame = new PrefsFrame();
        }
        prefsFrame.setVisible( true );
        prefsFrame.toFront();
    }

    protected Action getOpenAction()
    {
        return actionOpen;
    }

    protected ActionOpenRecent createOpenRecentAction( String name, File path )
    {
        return new ActionEisKOpenRecent( name, path );
    }

    public void openDocument( File f )
    {
        openDocument( f, true );
    }

    public void openDocument( File f, boolean postAction )
    {
        if( actionOpen.perform( f ) && postAction ) {
            final Preferences audioPrefs = getApplication().getUserPrefs().node( PrefsUtil.NODE_AUDIO );
            final String postActionValue = audioPrefs.get(PrefsUtil.KEY_AUTOPLAYFROMFINDER, PrefsUtil.AUTOPLAYFROMFINDER_NONE);
            if( !postActionValue.equals( PrefsUtil.AUTOPLAYFROMFINDER_NONE )) {
                final Session doc = findDocumentForPath( f );
                if( doc != null ) {
                    if( postActionValue.equals( PrefsUtil.AUTOPLAYFROMFINDER_LOOP )) {
                        final Span loopSpan = doc.getAudioTrail().getSpan();
                        // hmmm.... bit shaky all because we don't have a clean MVC
                        doc.timeline.setSelectionSpan( this, loopSpan );
                        doc.getFrame().setLoop( true );
//                        doc.getTransport().setLoop( loopSpan );
                    }
                    doc.getTransport().play( 1.0 );
                }
            }
        }
    }

    public void openDocument( File[] fs )
    {
        actionOpenMM.perform( fs );
    }

    public Session newDocument( AudioFileDescr afd )
    {
        return actionNewEmpty.perform( afd );
    }

    public void addSCPlugIn( Action a, String[] hierarchy )
    {
System.err.println( "addSCPlugIn : NOT YET WORKING" );
//		final JMenuItem mi = new JMenuItem( a );
////		sma.setProtoType( rbmi );
////		rbmi.putClientProperty( CLIENT_BG, CLIENT_BG + "window" );
//		addMenuItem( mSuperCollider, mi );	// XXX traverse hierarchy
    }

    public void removeSCPlugIn( Action a )
    {
System.err.println( "removeSCPlugIn : NOT YET WORKING" );
//		removeMenuItem( mSuperCollider, a );
    }

    protected Session findDocumentForPath( File f )
    {
        final de.sciss.app.DocumentHandler	dh	= AbstractApplication.getApplication().getDocumentHandler();
        Session								doc;
        AudioFileDescr[]					afds;

        for( int i = 0; i < dh.getDocumentCount(); i++ ) {
            doc		= (Session) dh.getDocument( i );
            afds	= doc.getDescr();
            for (AudioFileDescr afd : afds) {
                if ((afd.file != null) && afd.file.equals(f)) {
                    return doc;
                }
            }
        }
        return null;
    }

// ---------------- Action objects for file (session) operations ---------------- 

    // action for the New-Empty Document menu item
    @SuppressWarnings("serial")
    private class ActionNewEmpty
    extends MenuAction
    {
        private JPanel				p	= null;
        private AudioFileFormatPane	affp;

        protected ActionNewEmpty( String text, KeyStroke shortcut )
        {
            super( text, shortcut );
        }

        public void actionPerformed( ActionEvent e )
        {
            final AudioFileDescr afd = query();
            if( afd != null ) {
//				// XXX there is a bug with
//				// FloatingPaletteHandler
//				// causing infinite mutual window
//				// refresh if we open the new window
//				// straight away. The bug disappears
//				// if we defer the window opening...
//				EventQueue.invokeLater( new Runnable() {
//					public void run()
//					{
                        perform( afd );
//					}
//				});
            }
        }

        private AudioFileDescr query()
        {
            final AudioFileDescr		afd			= new AudioFileDescr();
//			final JOptionPane			dlg;
            final String[]				queryOptions = { getResourceString( "buttonCreate" ),
                                                         getResourceString( "buttonCancel" )};
            final int					result;
//			final Object				result;
//			final Component				c			= ((AbstractWindow) root.getComponent( Main.COMP_MAIN )).getWindow();
            final Server.Status			status;
            final double				sampleRate;
            final Param					param;
            final Preferences			audioPrefs;

            if( p == null ) {
                affp		= new AudioFileFormatPane( AudioFileFormatPane.NEW_FILE_FLAGS );
                p			= new JPanel( new BorderLayout() );
                p.add( affp, BorderLayout.NORTH );
//				AbstractWindowHandler.setDeepFont( affp );
            }

            status		= SuperColliderClient.getInstance().getStatus();
            if( status != null ) {
                sampleRate	= status.sampleRate;
            } else {
                audioPrefs	= getApplication().getUserPrefs().node( PrefsUtil.NODE_AUDIO );
                param		= Param.fromPrefs( audioPrefs, PrefsUtil.KEY_AUDIORATE, null );
                if( param != null ) {
                    sampleRate = param.val;
                } else {
                    sampleRate = 0.0;
                }
            }

//			System.out.println( "sampleRate " + sampleRate );

            if( sampleRate != 0.0 ) {
                affp.toDescr( afd );
                afd.rate = sampleRate;
                affp.fromDescr( afd );
            }

//			result		= JOptionPane.showOptionDialog( null, p, getValue( NAME ).toString(),
//					JOptionPane.YES_NO_OPTION, JOptionPane.QUESTION_MESSAGE,
//					null, queryOptions, queryOptions[ 0 ]);

            //  Object message, String title, int optionType, int messageType, Icon icon, Object[] options, Object initialValue)
            // (Object message, int messageType, int optionType, Icon icon, Object[] options, Object initialValue)
            final JOptionPane op = new JOptionPane( p, JOptionPane.QUESTION_MESSAGE, JOptionPane.YES_NO_OPTION, null, queryOptions, queryOptions[ 0 ]);
//		    final JDialog dlg = op.createDialog( null, getValue( NAME ).toString() );
//		    dlg.show();
//		    result = op.getValue();
            result = BasicWindowHandler.showDialog( op, null, getValue( NAME ).toString() );

//			if( queryOptions[ 0 ].equals( result )) {}
            if( result == 0 ) {
                affp.toDescr( afd );
                return afd;
            } else {
                return null;
            }
        }

        protected Session perform( AudioFileDescr afd )
        {
            final Session doc;

            try {
                doc = Session.newEmpty( afd );
                AbstractApplication.getApplication().getDocumentHandler().addDocument( this, doc );
                doc.createFrame();
                return doc;
            }
            catch( IOException e1 ) {	// should never happen
                BasicWindowHandler.showErrorDialog( null, e1, getValue( Action.NAME ).toString() );
                return null;
            }
        }
    }

    // action for the Open-Session menu item
    @SuppressWarnings("serial")
    private class ActionOpen
    extends MenuAction
    {
//		private String text;

        protected ActionOpen( String text, KeyStroke shortcut )
        {
            super( text, shortcut );

//			this.text = text;
        }

        /*
         *  Open a Session. If the current Session
         *  contains unsaved changes, the user is prompted
         *  to confirm. A file chooser will pop up for
         *  the user to select the session to open.
         */
        public void actionPerformed( ActionEvent e )
        {
            File f = queryFile();
            if( f != null ) perform( f );
        }

        private File queryFile()
        {
            final FileDialog		fDlg;
            final String			strFile, strDir;
            final AbstractWindow	w		= (AbstractWindow) getApplication().getComponent( Main.COMP_MAIN );
            final Frame				frame	= (w.getWindow() instanceof Frame) ? (Frame) w.getWindow() : null;
            final Preferences		prefs	= getApplication().getUserPrefs();

//System.err.println( "frame : "+frame );

            fDlg	= new FileDialog( frame, getResourceString( "fileDlgOpen" ), FileDialog.LOAD );
//			fDlg.setFilenameFilter( doc );
            fDlg.setDirectory( prefs.get( PrefsUtil.KEY_FILEOPENDIR, System.getProperty( "user.home" )));
            // fDlg.setFile();
            fDlg.setVisible( true );
            strDir	= fDlg.getDirectory();
            strFile	= fDlg.getFile();

            if( strFile == null ) return null;   // means the dialog was cancelled

            // save dir prefs
            prefs.put( PrefsUtil.KEY_FILEOPENDIR, strDir );

            return( new File( strDir, strFile ));
        }

        /**
         *  Loads a new document file.
         *  a <code>ProcessingThread</code>
         *  started which loads the new session.
         *
         *  @param  path	the file of the document to be loaded
         */
        protected boolean perform( File path )
        {
            Session	doc;

            // check if the document is already open
            doc = findDocumentForPath( path );
            if( doc != null ) {
                doc.getFrame().setVisible( true );
                doc.getFrame().toFront();
                return true;
            }

            try {
                doc		= Session.newFrom( path );
                addRecent( doc.getDisplayDescr().file );
                AbstractApplication.getApplication().getDocumentHandler().addDocument( this, doc );
                doc.createFrame();	// must be performed after the doc was added
                return true;
            }
            catch( IOException e1 ) {
                BasicWindowHandler.showErrorDialog( null, e1, getValue( Action.NAME ).toString() );
                return false;
            }
        }
    }

    // action for the Open-Multiple-Mono menu item
    @SuppressWarnings("serial")
    private class ActionOpenMM
    extends MenuAction
    {
        protected ActionOpenMM( String text, KeyStroke shortcut )
        {
            super( text, shortcut );
        }

        public void actionPerformed( ActionEvent e )
        {
            File[] fs = queryFiles();
            if( fs != null ) {
                if( fs.length == 0 ) {
                    final JOptionPane op = new JOptionPane( getResourceString( "errFileSelectionEmpty" ), JOptionPane.ERROR_MESSAGE );
                    BasicWindowHandler.showDialog( op, null, getValue( NAME ).toString() );
                    return;
                }
                perform( fs );
            }
        }

        private File[] queryFiles()
        {
            final JFileChooser	fDlg	= new JFileChooser();
            final int			result;
            final Component		c		= ((AbstractWindow) getApplication().getComponent( Main.COMP_MAIN )).getWindow();
            final Preferences	prefs	= getApplication().getUserPrefs();
            final File[]		files;

            fDlg.setMultiSelectionEnabled( true );
            fDlg.setDialogTitle( getValue( Action.NAME ).toString() );
            fDlg.setCurrentDirectory( new File( prefs.get( PrefsUtil.KEY_FILEOPENDIR, System.getProperty( "user.home" ))));
            result	= fDlg.showOpenDialog( c );

            if( result == JFileChooser.APPROVE_OPTION ) {
                files = fDlg.getSelectedFiles();
                // save dir prefs
                if( files.length > 0 ) {
                    prefs.put( PrefsUtil.KEY_FILEOPENDIR, files[ 0 ].getParent() );
                }
                return files;
            } else {
                return null;
            }
        }

        /**
         *  Loads a new document file.
         *  a <code>ProcessingThread</code>
         *  started which loads the new session.
         */
        protected void perform(File[] paths) {
            if (paths.length == 0) return;

            Session doc;

            // check if the document is already open
            for (File path : paths) {
                doc = findDocumentForPath(path);
                if (doc != null) {
                    doc.getFrame().setVisible(true);
                    doc.getFrame().toFront();
                    return;
                }
            }

            try {
                doc	= Session.newFrom( paths );
                addRecent( doc.getDisplayDescr().file );
                AbstractApplication.getApplication().getDocumentHandler().addDocument( this, doc );
                doc.createFrame();	// must be performed after the doc was added
            }
            catch( IOException e1 ) {
                BasicWindowHandler.showErrorDialog( null, e1, getValue( Action.NAME ).toString() );
            }
        }
    }

    // action for the Open-Recent menu
    @SuppressWarnings("serial")
    private class ActionEisKOpenRecent
    extends ActionOpenRecent
    {
        private File[]	paths;

        // new action with path set to null
        protected ActionEisKOpenRecent( String text, File path )
        {
            super( text, path );
        }

        // set the path of the action. this
        // is the file that will be loaded
        // if the action is performed
        protected void setPath( File path )
        {
            paths			= new File[] { path };
            boolean enable	= false;
            try {
                if( path == null ) return;
                if( path.isFile() ) {
                    enable	= true;
                    return;
                }

                final String			name		= path.getName();
                final int				idxOpenBr	= name.indexOf( '[' );
                final int				idxCloseBr	= name.indexOf( ']', idxOpenBr + 1 );
//				System.out.println( "for '" + name + "' idxOpenBr = " + idxOpenBr + "; idxCloseBr = " + idxCloseBr );
                if( (idxOpenBr < 0) || ((idxOpenBr + 1) >= (idxCloseBr - 1)) ) return;

                final File				parent		= path.getParentFile();
                final String			pre			= name.substring( 0, idxOpenBr );
                final String			post		= name.substring( idxCloseBr + 1 );
                final StringTokenizer	tok			= new StringTokenizer(
                    name.substring( idxOpenBr + 1, idxCloseBr ), "," );
                paths	= new File[ tok.countTokens() ];
                enable	= true;
                for( int i = 0; i < paths.length; i++ ) {
                    paths[ i ] = new File( parent, pre + tok.nextToken() + post );
//					System.out.println( "testing path: '" + paths[ i ].getAbsolutePath() + "'" );
                    enable   &= paths[ i ].isFile();
                }
            }
            finally {
                setEnabled( enable );
            }
        }

        /**
         *  If a path was set for the
         *  action and the user confirms
         *  an intermitting confirm-unsaved-changes
         *  dialog, the new session will be loaded
         */
        public void actionPerformed( ActionEvent e )
        {
            if( paths.length == 1 ) {
                if( paths[ 0 ] == null ) return;
                openDocument( paths[ 0 ], false );
            } else {
                openDocument( paths );
            }
        }
    } // class actionOpenRecentClass

// ---------------- Action objects for window operations ---------------- 

    // action for the IOSetup menu item
    @SuppressWarnings("serial")
    private class ActionIOSetup
    extends MenuAction
    {
        protected ActionIOSetup( String text, KeyStroke shortcut )
        {
            super( text, shortcut );
        }

        /**
         *  Brings up the IOSetup
         */
        public void actionPerformed( ActionEvent e )
        {
            IOSetupFrame f = (IOSetupFrame) getApplication().getComponent( Main.COMP_IOSETUP );

            if( f == null ) {
                f = new IOSetupFrame();		// automatically adds component
            }
            f.setVisible( true );
            f.toFront();
        }
    }

    // action for the Control Room menu item
    @SuppressWarnings("serial")
    private class ActionCtrlRoom
    extends MenuAction
    {
        protected ActionCtrlRoom( String text, KeyStroke shortcut )
        {
            super( text, shortcut );
        }

        /**
         *  Brings up the IOSetup
         */
        public void actionPerformed( ActionEvent e )
        {
            ControlRoomFrame f = (ControlRoomFrame) getApplication().getComponent( Main.COMP_CTRLROOM );

            if( f == null ) {
                f = new ControlRoomFrame();	// automatically adds component
            }
            f.setVisible( true );
            f.toFront();
        }
    }

    // action for the Observer menu item
    @SuppressWarnings("serial")
    private class ActionObserver
    extends MenuAction
    {
        protected ActionObserver( String text, KeyStroke shortcut )
        {
            super( text, shortcut );
        }

        /**
         *  Brings up the IOSetup
         */
        public void actionPerformed( ActionEvent e )
        {
            ObserverPalette f = (ObserverPalette) getApplication().getComponent( Main.COMP_OBSERVER );

            if( f == null ) {
                f = new ObserverPalette();	// automatically adds component
            }
            f.setVisible( true );
            f.toFront();
        }
    }

    // action for the About menu item
    private class ActionAbout extends MenuAction {

        protected ActionAbout(String text, KeyStroke shortcut) {
            super(text, shortcut);
        }

        /**
         * Brings up the About-Box
         */
        public void actionPerformed(ActionEvent e) {
            JFrame aboutBox = (JFrame) getApplication().getComponent(AboutBox.COMP_ABOUTBOX);

            if (aboutBox == null) {
                final AboutBox res = new AboutBox();
                res.setCreditsPreferredSize(new Dimension(300, 150));
                res.pack();
                aboutBox = res;
            }
            aboutBox.setVisible(true);
            aboutBox.toFront();
        }
    }
}