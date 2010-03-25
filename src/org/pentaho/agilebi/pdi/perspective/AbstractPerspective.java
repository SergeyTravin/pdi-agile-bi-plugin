package org.pentaho.agilebi.pdi.perspective;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.DocumentHelper;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.pentaho.agilebi.pdi.PDIMessages;
import org.pentaho.agilebi.pdi.modeler.ModelerController;
import org.pentaho.agilebi.pdi.modeler.ModelerWorkspace;
import org.pentaho.agilebi.pdi.modeler.ModelerWorkspaceUtil;
import org.pentaho.di.core.EngineMetaInterface;
import org.pentaho.di.ui.spoon.FileListener;
import org.pentaho.di.ui.spoon.Spoon;
import org.pentaho.di.ui.spoon.SpoonPerspective;
import org.pentaho.di.ui.spoon.SpoonPerspectiveListener;
import org.pentaho.metadata.model.LogicalModel;
import org.pentaho.metadata.util.MondrianModelExporter;
import org.pentaho.ui.xul.XulComponent;
import org.pentaho.ui.xul.XulDomContainer;
import org.pentaho.ui.xul.XulException;
import org.pentaho.ui.xul.XulOverlay;
import org.pentaho.ui.xul.XulRunner;
import org.pentaho.ui.xul.binding.Binding;
import org.pentaho.ui.xul.binding.BindingConvertor;
import org.pentaho.ui.xul.binding.BindingFactory;
import org.pentaho.ui.xul.binding.DefaultBindingFactory;
import org.pentaho.ui.xul.components.XulTab;
import org.pentaho.ui.xul.components.XulTabpanel;
import org.pentaho.ui.xul.containers.XulTabbox;
import org.pentaho.ui.xul.containers.XulTabpanels;
import org.pentaho.ui.xul.containers.XulTabs;
import org.pentaho.ui.xul.dom.Document;
import org.pentaho.ui.xul.impl.AbstractXulEventHandler;
import org.pentaho.ui.xul.impl.XulEventHandler;
import org.pentaho.ui.xul.swt.SwtXulLoader;
import org.pentaho.ui.xul.swt.SwtXulRunner;
import org.pentaho.ui.xul.swt.tags.SwtTab;
import org.pentaho.ui.xul.util.XulDialogCallback;

public abstract class AbstractPerspective extends AbstractXulEventHandler implements SpoonPerspective, FileListener{

  protected XulDomContainer container;
  protected XulRunner runner;
  private Log logger = LogFactory.getLog(AgileBiModelerPerspective.class);
  protected Document document;
  protected XulTabs tabs;
  protected XulTabpanels panels;
  protected XulTabbox tabbox;
  protected List<SpoonPerspectiveListener> listeners = new ArrayList<SpoonPerspectiveListener>();
  private String defaultExtension = "";
  protected ModelerWorkspace model;
  protected EngineMetaInterface selectedMeta;
  
  protected Map<XulTab, EngineMetaInterface> metas = new HashMap<XulTab, EngineMetaInterface>();
  
  protected AbstractPerspective(String perspectiveSrc) {
    try {
      SwtXulLoader loader = new SwtXulLoader();
      loader.registerClassLoader(getClass().getClassLoader());
      container = loader.loadXul(perspectiveSrc, new PDIMessages(this.getClass())); //$NON-NLS-1$
      
      runner = new SwtXulRunner();
      runner.addContainer(container);
      runner.initialize();
      
      document = container.getDocumentRoot();
      container.addEventHandler(this);
      tabs = (XulTabs) document.getElementById("tabs");
      panels = (XulTabpanels) document.getElementById("tabpanels");
      tabbox = (XulTabbox) tabs.getParent();
      BindingFactory bf = new DefaultBindingFactory();
      bf.setDocument(document);
//      bf.setBindingType(Binding.Type.ONE_WAY);
      
      
      bf.createBinding(tabbox, "selectedIndex", this, "selectedMeta", new BindingConvertor<Integer, EngineMetaInterface>(){
        public EngineMetaInterface sourceToTarget(Integer value) {
          return metas.get(tabs.getTabByIndex(value));
        }
        public Integer targetToSource(EngineMetaInterface value) {
          for(XulTab tab : metas.keySet()){
            if(metas.get(tab) == value){
              return tab.getParent().getChildNodes().indexOf(tab);
            }
          }
          return -1;
        }
      });
      
    } catch(Exception e){
      // TODO: throw exception
      logger.error(e);
    }
  }
  


  public abstract String getDisplayName(Locale l);

  public abstract InputStream getPerspectiveIcon();

  public void setActive(boolean active) {
    for(SpoonPerspectiveListener l : listeners){
      if(active){
        l.onActivation();
      } else {
        l.onDeactication();
      }
    }

  }

  public abstract String getId();

  public final Composite getUI() {
    return (Composite) container.getDocumentRoot().getRootElement().getFirstChild().getManagedObject();
  }

  // ======== File Listener ====== //

  public boolean accepts(String fileName) {
    if(fileName == null || fileName.indexOf('.') == -1){
      return false;
    }
    String extension = fileName.substring(fileName.lastIndexOf('.')+1);
    return extension.equals(defaultExtension);
  }
  

  public boolean acceptsXml(String nodeName) {
    return false;
  }

  public abstract String[] getFileTypeDisplayNames(Locale locale);

  public String getRootNodeName() {
    return null;
  }

  public abstract String[] getSupportedExtensions();

  public abstract boolean save(EngineMetaInterface meta, String fname, boolean isExport);

  public void syncMetaName(EngineMetaInterface meta, String name) {
  }

  public static String createShortName( String filename ) {
    if( filename == null ) {
      return null;
    }
    int extensionPos = filename.lastIndexOf('.');
    if( extensionPos == -1 ) {
      extensionPos = filename.length();
    }
    int sepPos = filename.replace('\\', '/').lastIndexOf('/');
    if( sepPos == -1 ) {
      sepPos = 0;
    } else {
      sepPos++;
    }
    return filename.substring(sepPos, extensionPos);    
  }
  
  public abstract List<XulEventHandler> getEventHandlers();

  public abstract List<XulOverlay> getOverlays();

  public void addPerspectiveListener(SpoonPerspectiveListener listener) {
    if(listeners.contains(listener) == false){
      listeners.add(listener);
    }
  }

  @Override
  public abstract String getName();
  
  public abstract boolean onTabClose(final int pos) throws XulException;
 
  protected static class CloseConfirmXulDialogCallback implements XulDialogCallback<Object>{
    public boolean closeIt = false;
    public void onClose(XulComponent sender, Status returnCode, Object retVal) {
      if(returnCode == Status.ACCEPT){
        closeIt = true;
      }
    }
    public void onError(XulComponent sender, Throwable t) {}
  }

  protected String getDefaultExtension() {
    return defaultExtension;
  }

  protected void setDefaultExtension(String defaultExtension) {
    this.defaultExtension = defaultExtension;
  }

  public class XulTabAndPanel{
    public XulTab tab;
    public XulTabpanel panel;
    public XulTabAndPanel(XulTab tab, XulTabpanel panel){
      this.tab = tab;
      this.panel = panel;
    }
  }
  
  public XulTabAndPanel createTab(){

    try {
      XulTab tab = (XulTab) document.createElement("tab");
      if(name != null){
        tab.setLabel(name);
      }
      XulTabpanel panel = (XulTabpanel) document.createElement("tabpanel"); //$NON-NLS-1
      panel.setSpacing(0);
      panel.setPadding(0);
      
      tabs.addChild(tab);
      panels.addChild(panel);
      //tabbox.setSelectedIndex(panels.getChildNodes().indexOf(panel));

      return new XulTabAndPanel(tab, panel);
      
    } catch (XulException e) {
      e.printStackTrace();
    }
    return null;
  }
  
  public void setNameForTab(XulTab tab, String name){
    String tabName = name;
    List<String> usedNames = new ArrayList<String>();
    for(XulComponent c : tabs.getChildNodes()){
      if(c != tab){
        usedNames.add(((SwtTab) c).getLabel());
      }
    }
    if(usedNames.contains(name)){
      int num = 2;
      while(true){
        tabName = name +" ("+num+")";
        if(usedNames.contains(tabName) == false){
          break;
        }
        num++;
      }
    }
    
    tab.setLabel(tabName);
  }
  
  protected static class NameBindingConvertor extends BindingConvertor<String, String>{
    AbstractPerspective per;
    XulTab tab;
    public NameBindingConvertor(AbstractPerspective per, XulTab tab){
      this.per = per;
      this.tab = tab;
    }
    @Override
    public String sourceToTarget(String value) {
      String tabName = value;
      List<String> usedNames = new ArrayList<String>();
      for(XulComponent c : per.tabs.getChildNodes()){
        if(c != tab){
          usedNames.add(((SwtTab) c).getLabel());
        }
      }
      if(usedNames.contains(value)){
        int num = 2;
        while(true){
          tabName = value+" ("+num+")";
          if(usedNames.contains(tabName) == false){
            break;
          }
          num++;
        }
      }
      return tabName;
    }
    @Override
    public String targetToSource(String value) {return value;}
  };
  
  public void setMetaForTab(XulTab tab, EngineMetaInterface meta){
    metas.put(tab, meta);
  }

  public EngineMetaInterface getActiveMeta() {
    int idx = tabbox.getSelectedIndex();
    if( idx == -1 ) {
      return null;
    }
    return metas.get(tabbox.getTabs().getChildNodes().get( idx ));
  }
  
  public void setSelectedMeta(EngineMetaInterface meta){
    EngineMetaInterface prevVal = this.selectedMeta;
    this.selectedMeta = meta;
    firePropertyChange("selectedMeta", prevVal, meta);
  }
  
  public EngineMetaInterface getSelectedMeta(){
    return selectedMeta;
  }
  
}
