/*******************************************************************************
 * Copyright (c) 2000-2012 Liferay, Inc. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or modify it under
 * the terms of the GNU Lesser General Public License as published by the Free
 * Software Foundation; either version 2.1 of the License, or (at your option)
 * any later version.
 *
 * This library is distributed in the hope that it will be useful, but WITHOUT
 * ANY WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS
 * FOR A PARTICULAR PURPOSE. See the GNU Lesser General Public License for more
 * details.
 *
 *******************************************************************************/

package com.liferay.ide.project.core.util;

import com.liferay.ide.core.util.CoreUtil;
import com.liferay.ide.project.core.IProjectDefinition;
import com.liferay.ide.project.core.PluginClasspathContainerInitializer;
import com.liferay.ide.project.core.ProjectCorePlugin;
import com.liferay.ide.project.core.ProjectRecord;
import com.liferay.ide.project.core.facet.IPluginFacetConstants;
import com.liferay.ide.project.core.facet.IPluginProjectDataModelProperties;
import com.liferay.ide.project.core.facet.PluginFacetProjectCreationDataModelProvider;
import com.liferay.ide.sdk.ISDKConstants;
import com.liferay.ide.sdk.util.SDKUtil;
import com.liferay.ide.server.util.ServerUtil;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.apache.commons.lang.WordUtils;
import org.eclipse.core.resources.IContainer;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IFolder;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IProjectDescription;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.resources.IWorkspace;
import org.eclipse.core.resources.IWorkspaceRunnable;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IPath;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.Path;
import org.eclipse.core.runtime.Platform;
import org.eclipse.core.runtime.SubProgressMonitor;
import org.eclipse.jdt.core.IClasspathAttribute;
import org.eclipse.jdt.core.IClasspathEntry;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jst.common.project.facet.core.JavaFacet;
import org.eclipse.jst.j2ee.jsp.JspFactory;
import org.eclipse.jst.j2ee.jsp.TagLibRefType;
import org.eclipse.jst.j2ee.project.facet.IJ2EEFacetConstants;
import org.eclipse.jst.j2ee.project.facet.IJ2EEFacetInstallDataModelProperties;
import org.eclipse.osgi.util.NLS;
import org.eclipse.ui.internal.ide.StatusUtil;
import org.eclipse.ui.internal.wizards.datatransfer.DataTransferMessages;
import org.eclipse.ui.statushandlers.StatusManager;
import org.eclipse.wst.common.componentcore.ComponentCore;
import org.eclipse.wst.common.componentcore.datamodel.properties.IFacetProjectCreationDataModelProperties;
import org.eclipse.wst.common.componentcore.datamodel.properties.IFacetProjectCreationDataModelProperties.FacetDataModelMap;
import org.eclipse.wst.common.componentcore.internal.operation.IArtifactEditOperationDataModelProperties;
import org.eclipse.wst.common.componentcore.internal.util.IModuleConstants;
import org.eclipse.wst.common.componentcore.resources.IVirtualComponent;
import org.eclipse.wst.common.componentcore.resources.IVirtualFolder;
import org.eclipse.wst.common.componentcore.resources.IVirtualResource;
import org.eclipse.wst.common.frameworks.datamodel.DataModelFactory;
import org.eclipse.wst.common.frameworks.datamodel.DataModelPropertyDescriptor;
import org.eclipse.wst.common.frameworks.datamodel.IDataModel;
import org.eclipse.wst.common.project.facet.core.IFacetedProject;
import org.eclipse.wst.common.project.facet.core.IFacetedProjectWorkingCopy;
import org.eclipse.wst.common.project.facet.core.IPreset;
import org.eclipse.wst.common.project.facet.core.IProjectFacet;
import org.eclipse.wst.common.project.facet.core.IProjectFacetVersion;
import org.eclipse.wst.common.project.facet.core.ProjectFacetsManager;
import org.eclipse.wst.common.project.facet.core.internal.FacetedProjectWorkingCopy;
import org.eclipse.wst.common.project.facet.core.runtime.IRuntime;
import org.eclipse.wst.common.project.facet.core.runtime.internal.BridgedRuntime;

/**
 * @author Gregory Amerson
 */
@SuppressWarnings( "restriction" )
public class ProjectUtil
{

    public static final String METADATA_FOLDER = ".metadata";

    public static void addTldToWebXml( final IProject project, String uriValue, String taglibLocation )
    {
        WebXMLDescriptorHelper webXmlHelper = new WebXMLDescriptorHelper( project );
        TagLibRefType tagLibRefType = JspFactory.eINSTANCE.createTagLibRefType();
        tagLibRefType.setTaglibURI( uriValue );
        tagLibRefType.setTaglibLocation( taglibLocation );
        
        try
        {
            webXmlHelper.addTagLib( tagLibRefType );
        }
        catch( Exception e )
        {
            ProjectCorePlugin.logError( "Failed to add taglib reference " + uriValue + ":" + taglibLocation, e );
        }
    }

    public static boolean collectProjectsFromDirectory(
        Collection<File> eclipseProjectFiles, Collection<File> liferayProjectDirs, File directory,
        Set<String> directoriesVisited, boolean recurse, IProgressMonitor monitor )
    {
        if( monitor.isCanceled() )
        {
            return false;
        }

        monitor.subTask( NLS.bind( DataTransferMessages.WizardProjectsImportPage_CheckingMessage, directory.getPath() ) );

        File[] contents = directory.listFiles();

        if( contents == null )
        {
            return false;
        }

        // Initialize recursion guard for recursive symbolic links
        if( directoriesVisited == null )
        {
            directoriesVisited = new HashSet<String>();

            try
            {
                directoriesVisited.add( directory.getCanonicalPath() );
            }
            catch( IOException exception )
            {
                StatusManager.getManager().handle(
                    StatusUtil.newStatus( IStatus.ERROR, exception.getLocalizedMessage(), exception ) );
            }
        }

        // first look for project description files
        final String dotProject = IProjectDescription.DESCRIPTION_FILE_NAME;

        for( int i = 0; i < contents.length; i++ )
        {
            File file = contents[i];

            if( isLiferayProjectDir( file ) )
            {
                // recurse to see if it has project file
                int currentSize = eclipseProjectFiles.size();

                collectProjectsFromDirectory(
                    eclipseProjectFiles, liferayProjectDirs, contents[i], directoriesVisited, false, monitor );

                int newSize = eclipseProjectFiles.size();

                if( newSize == currentSize )
                {
                    liferayProjectDirs.add( file );
                }
            }
            else if( file.isFile() && file.getName().equals( dotProject ) )
            {
                if( !eclipseProjectFiles.contains( file ) && isLiferayProjectDir( file.getParentFile() ) )
                {
                    eclipseProjectFiles.add( file );

                    // don't search sub-directories since we can't have nested
                    // projects

                    return true;
                }
            }
        }

        // no project description found, so recurse into sub-directories
        for( int i = 0; i < contents.length; i++ )
        {
            if( contents[i].isDirectory() )
            {
                if( !contents[i].getName().equals( METADATA_FOLDER ) )
                {
                    try
                    {
                        String canonicalPath = contents[i].getCanonicalPath();

                        if( !directoriesVisited.add( canonicalPath ) )
                        {

                            // already been here --> do not recurse
                            continue;
                        }
                    }
                    catch( IOException exception )
                    {
                        StatusManager.getManager().handle(
                            StatusUtil.newStatus( IStatus.ERROR, exception.getLocalizedMessage(), exception ) );
                    }

                    // dont recurse directories that we have already determined
                    // are Liferay projects
                    if( !liferayProjectDirs.contains( contents[i] ) && recurse )
                    {
                        collectProjectsFromDirectory(
                            eclipseProjectFiles, liferayProjectDirs, contents[i], directoriesVisited, recurse, monitor );
                    }
                }
            }
        }

        return true;
    }

    public static String convertToDisplayName( String name )
    {
        if( CoreUtil.isNullOrEmpty( name ) )
        {
            return "";
        }

        String displayName = removePluginSuffix( name );

        displayName = displayName.replaceAll( "-", " " ).replaceAll( "_", " " );

        displayName = WordUtils.capitalize( displayName );

        return displayName;
    }

    public static void createDefaultWebXml( File webxmlFile )
    {
        final String webXmlContents = // "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<!DOCTYPE web-app PUBLIC \"-//Sun Microsystems, Inc.//DTD Web Application 2.3//EN\" \"http://java.sun.com/dtd/web-app_2_3.dtd\">\n<web-app>\n</web-app>";
            "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<web-app id=\"WebApp_ID\" version=\"2.5\" xmlns=\"http://java.sun.com/xml/ns/javaee\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/web-app_2_5.xsd\">\n</web-app>"; //$NON-NLS-1$

        try
        {
            org.eclipse.wst.common.project.facet.core.util.internal.FileUtil.writeFile( webxmlFile, webXmlContents );
        }
        catch( Exception e )
        {
            ProjectCorePlugin.logError( "Unable to create default web xml", e );
        }
    }

    public static IFile createEmptyProjectFile( String fileName, IFolder folder ) throws CoreException
    {
        IFile emptyFile = folder.getFile( fileName );

        if( emptyFile.exists() )
        {
            return emptyFile;
        }
        else
        {
            emptyFile.create( new ByteArrayInputStream( "".getBytes() ), true, null );
        }

        return emptyFile;
    }

    public static IProject createExistingProject(
        final ProjectRecord record, IRuntime runtime, String sdkLocation, IProgressMonitor monitor )
        throws CoreException
    {
        String projectName = record.getProjectName();

        final IWorkspace workspace = ResourcesPlugin.getWorkspace();

        IProject project = workspace.getRoot().getProject( projectName );

        if( record.description == null )
        {
            // error case
            record.description = workspace.newProjectDescription( projectName );

            IPath locationPath = new Path( record.projectSystemFile.getAbsolutePath() );

            // If it is under the root use the default location
            if( Platform.getLocation().isPrefixOf( locationPath ) )
            {
                record.description.setLocation( null );
            }
            else
            {
                record.description.setLocation( locationPath );
            }
        }
        else
        {
            record.description.setName( projectName );
        }

        monitor.beginTask( "Importing project", 100 );

        project.create( record.description, new SubProgressMonitor( monitor, 30 ) );

        project.open( IResource.FORCE, new SubProgressMonitor( monitor, 70 ) );

        // need to check to see if we an ext project with source folders with incorrect parent attributes
        if( project.getName().endsWith( ISDKConstants.EXT_PLUGIN_PROJECT_SUFFIX ) )
        {
            fixExtProjectClasspathEntries( project );
        }

        IFacetedProject fProject = ProjectFacetsManager.create( project, true, monitor );

        FacetedProjectWorkingCopy fpwc = new FacetedProjectWorkingCopy( fProject );

        PluginFacetUtil.configureProjectAsPlugin( fpwc, runtime, sdkLocation );

        fpwc.commitChanges( monitor );

        final IJavaProject javaProject = JavaCore.create( fProject.getProject() );

        ResourcesPlugin.getWorkspace().run( new IWorkspaceRunnable()
        {
            public void run( IProgressMonitor monitor ) throws CoreException
            {
                for( IClasspathEntry entry : javaProject.getRawClasspath() )
                {
                    if( entry.getEntryKind() == IClasspathEntry.CPE_CONTAINER &&
                        entry.getPath().segment( 0 ).equals( PluginClasspathContainerInitializer.ID ) )
                    {
                        JavaCore.getClasspathContainerInitializer( PluginClasspathContainerInitializer.ID ).initialize(
                            entry.getPath(), javaProject );
                        break;
                    }
                }

                monitor.done();
            }
        }, monitor );

        return project;
    }

    public static IProject createNewProject(
        ProjectRecord projectRecord, IRuntime runtime, String sdkLocation, IProgressMonitor monitor )
        throws CoreException
    {
        IDataModel newProjectDataModel =
            DataModelFactory.createDataModel( new PluginFacetProjectCreationDataModelProvider() );

        // we are importing so set flag to not create anything
        newProjectDataModel.setBooleanProperty( IPluginProjectDataModelProperties.CREATE_PROJECT_OPERATION, false );

        String sdkName = PluginFacetUtil.getSDKName( sdkLocation );
        // if the get sdk from the location
        newProjectDataModel.setProperty( IPluginProjectDataModelProperties.LIFERAY_SDK_NAME, sdkName );

        setGenerateDD( newProjectDataModel, false );

        // TODO IDE-110 IDE-648
        IPath webXmlPath = projectRecord.getProjectLocation().append( "docroot/WEB-INF/web.xml" );

        if( projectRecord.getProjectName().endsWith( ISDKConstants.PORTLET_PLUGIN_PROJECT_SUFFIX ) )
        {
            newProjectDataModel.setProperty( IPluginProjectDataModelProperties.PLUGIN_TYPE_PORTLET, true );

            if( !( webXmlPath.toFile().exists() ) )
            {
                createDefaultWebXml( webXmlPath.toFile() );
            }
        }
        else if( projectRecord.getProjectName().endsWith( ISDKConstants.HOOK_PLUGIN_PROJECT_SUFFIX ) )
        {
            newProjectDataModel.setProperty( IPluginProjectDataModelProperties.PLUGIN_TYPE_HOOK, true );

            if( !( webXmlPath.toFile().exists() ) )
            {
                createDefaultWebXml( webXmlPath.toFile() );
            }
        }
        else if( projectRecord.getProjectName().endsWith( ISDKConstants.EXT_PLUGIN_PROJECT_SUFFIX ) )
        {
            // TODO IDE-110 IDE-648
            webXmlPath =
                webXmlPath.removeLastSegments( 3 ).append( new Path( "docroot/WEB-INF/ext-web/docroot/WEB-INF/web.xml" ) );

            newProjectDataModel.setProperty( IPluginProjectDataModelProperties.PLUGIN_TYPE_EXT, true );

            if( !( webXmlPath.toFile().exists() ) )
            {
                createDefaultWebXml( webXmlPath.toFile() );
            }
        }
        else if( projectRecord.getProjectName().endsWith( ISDKConstants.LAYOUTTPL_PLUGIN_PROJECT_SUFFIX ) )
        {
            newProjectDataModel.setProperty( IPluginProjectDataModelProperties.PLUGIN_TYPE_LAYOUTTPL, true );
        }
        else if( projectRecord.getProjectName().endsWith( ISDKConstants.THEME_PLUGIN_PROJECT_SUFFIX ) )
        {
            newProjectDataModel.setProperty( IPluginProjectDataModelProperties.PLUGIN_TYPE_THEME, true );
        }

        IFacetedProjectWorkingCopy fpjwc =
            (IFacetedProjectWorkingCopy) newProjectDataModel.getProperty( IFacetProjectCreationDataModelProperties.FACETED_PROJECT_WORKING_COPY );
        fpjwc.setProjectName( projectRecord.getProjectName() );
        fpjwc.setProjectLocation( projectRecord.getProjectLocation() );

        PluginFacetUtil.configureProjectAsPlugin( fpjwc, runtime, sdkLocation );

        fpjwc.commitChanges( monitor );

        return fpjwc.getProject();
    }

    public static IFile findServiceJarForContext( String context )
    {
        IProject[] projects = ResourcesPlugin.getWorkspace().getRoot().getProjects();

        for( IProject project : projects )
        {
            if( project.getName().equals( context ) )
            {
                // IDE-110 IDE-648
                IVirtualFolder webappRoot = CoreUtil.getDocroot( project );

                if( webappRoot != null )
                {
                    for( IContainer container : webappRoot.getUnderlyingFolders() )
                    {
                        if( container != null && container.exists() )
                        {
                            final Path path = new Path( "WEB-INF/lib/" + project.getName() + "-service.jar" );
                            IFile serviceJar = container.getFile( path );

                            if( serviceJar.exists() )
                            {
                                return serviceJar;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private static void fixExtProjectClasspathEntries( IProject project )
    {
        try
        {
            boolean fixedAttr = false;

            IJavaProject javaProject = JavaCore.create( project );

            List<IClasspathEntry> newEntries = new ArrayList<IClasspathEntry>();

            IClasspathEntry[] entries = javaProject.getRawClasspath();

            for( IClasspathEntry entry : entries )
            {
                IClasspathEntry newEntry = null;

                if( entry.getEntryKind() == IClasspathEntry.CPE_SOURCE )
                {
                    List<IClasspathAttribute> newAttrs = new ArrayList<IClasspathAttribute>();

                    IClasspathAttribute[] attrs = entry.getExtraAttributes();

                    if( !CoreUtil.isNullOrEmpty( attrs ) )
                    {
                        for( IClasspathAttribute attr : attrs )
                        {
                            IClasspathAttribute newAttr = null;

                            if( "owner.project.facets".equals( attr.getName() ) &&
                                "liferay.plugin".equals( attr.getValue() ) )
                            {
                                newAttr = JavaCore.newClasspathAttribute( attr.getName(), "liferay.ext" );
                                fixedAttr = true;
                            }
                            else
                            {
                                newAttr = attr;
                            }

                            newAttrs.add( newAttr );
                        }

                        newEntry =
                            JavaCore.newSourceEntry(
                                entry.getPath(), entry.getInclusionPatterns(), entry.getExclusionPatterns(),
                                entry.getOutputLocation(), newAttrs.toArray( new IClasspathAttribute[0] ) );
                    }
                }

                if( newEntry == null )
                {
                    newEntry = entry;
                }

                newEntries.add( newEntry );
            }

            if( fixedAttr )
            {
                IProgressMonitor monitor = new NullProgressMonitor();

                javaProject.setRawClasspath( newEntries.toArray( new IClasspathEntry[0] ), monitor );

                try
                {
                    javaProject.getProject().refreshLocal( IResource.DEPTH_INFINITE, monitor );
                }
                catch( Exception e )
                {
                    ProjectCorePlugin.logError( e );
                }
            }

            fixExtProjectSrcFolderLinks( project );
        }
        catch( Exception ex )
        {
            ProjectCorePlugin.logError( "Exception trying to fix Ext project classpath entries.", ex );
        }
    }

    /** IDE-270 */
    public static void fixExtProjectSrcFolderLinks( IProject extProject ) throws JavaModelException
    {
        if( extProject != null )
        {
            IJavaProject javaProject = JavaCore.create( extProject );

            if( javaProject != null )
            {
                final IVirtualComponent c = ComponentCore.createComponent( extProject, false );

                if( c != null )
                {
                    final IVirtualFolder jsrc = c.getRootFolder().getFolder( "/WEB-INF/classes" );

                    if( jsrc != null )
                    {
                        final IClasspathEntry[] cp = javaProject.getRawClasspath();

                        for( int i = 0; i < cp.length; i++ )
                        {
                            final IClasspathEntry cpe = cp[i];

                            if( cpe.getEntryKind() == IClasspathEntry.CPE_SOURCE )
                            {
                                if( cpe.getPath().removeFirstSegments( 1 ).segmentCount() > 0 )
                                {
                                    try
                                    {
                                        IFolder srcFolder =
                                            ResourcesPlugin.getWorkspace().getRoot().getFolder( cpe.getPath() );

                                        IVirtualResource[] virtualResource = ComponentCore.createResources( srcFolder );

                                        // create link for source folder only when it is not mapped
                                        if( virtualResource.length == 0 )
                                        {
                                            jsrc.createLink( cpe.getPath().removeFirstSegments( 1 ), 0, null );
                                        }
                                    }
                                    catch( Exception e )
                                    {
                                        ProjectCorePlugin.logError( e );
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    public static IFacetedProject getFacetedProject( IProject project )
    {
        try
        {
            return ProjectFacetsManager.create( project );
        }
        catch( CoreException e )
        {
            return null;
        }
    }

    public static Set<IProjectFacetVersion> getFacetsForPreset( String presetId )
    {
        IPreset preset = ProjectFacetsManager.getPreset( presetId );
        return preset.getProjectFacets();
    }

    public static IProjectFacet getLiferayFacet( IFacetedProject facetedProject )
    {
        for( IProjectFacetVersion projectFacet : facetedProject.getProjectFacets() )
        {
            if( isLiferayFacet( projectFacet.getProjectFacet() ) )
            {
                return projectFacet.getProjectFacet();
            }
        }

        return null;
    }

    public static String getLiferayPluginType( String projectLocation )
    {
        if( isLiferayProjectDir( new File( projectLocation ) ) )
        {
            String suffix = "";

            if( projectLocation.endsWith( ISDKConstants.PORTLET_PLUGIN_PROJECT_SUFFIX ) )
            {
                suffix = ISDKConstants.PORTLET_PLUGIN_PROJECT_SUFFIX;
            }
            else if( projectLocation.endsWith( ISDKConstants.HOOK_PLUGIN_PROJECT_SUFFIX ) )
            {
                suffix = ISDKConstants.HOOK_PLUGIN_PROJECT_SUFFIX;
            }
            else if( projectLocation.endsWith( ISDKConstants.EXT_PLUGIN_PROJECT_SUFFIX ) )
            {
                suffix = ISDKConstants.EXT_PLUGIN_PROJECT_SUFFIX;
            }
            else if( projectLocation.endsWith( ISDKConstants.LAYOUTTPL_PLUGIN_PROJECT_SUFFIX ) )
            {
                suffix = ISDKConstants.LAYOUTTPL_PLUGIN_PROJECT_SUFFIX;
            }
            else if( projectLocation.endsWith( ISDKConstants.THEME_PLUGIN_PROJECT_SUFFIX ) )
            {
                suffix = ISDKConstants.THEME_PLUGIN_PROJECT_SUFFIX;
            }

            return suffix.replace( "-", "" );
        }

        return null;
    }

    public static IFile getPortletXmlFile( IProject project )
    {
        if( project != null && ProjectUtil.isLiferayProject( project ) )
        {
            // IDE-110 IDE-648
            final IVirtualFolder webappRoot = CoreUtil.getDocroot( project );

            if( webappRoot != null )
            {
                for( IContainer container : webappRoot.getUnderlyingFolders() )
                {
                    if( container != null && container.exists() )
                    {
                        IFile file = container.getFile( new Path( "WEB-INF/portlet.xml" ) );

                        if( file.exists() )
                        {
                            return file;
                        }
                    }
                }
            }
        }

        return null;
    }

    public static IProject getProject( IDataModel model )
    {
        if( model != null )
        {
            String projectName = model.getStringProperty( IArtifactEditOperationDataModelProperties.PROJECT_NAME );
            return CoreUtil.getProject( projectName );
        }

        return null;
    }

    public static IProject getProject( String projectName )
    {
        return ResourcesPlugin.getWorkspace().getRoot().getProject( projectName );
    }

    public static ProjectRecord getProjectRecordForDir( String dir )
    {
        ProjectRecord projectRecord = null;
        File projectDir = new File( dir );

        if( isLiferayProjectDir( projectDir ) )
        {
            // determine if this is a previous eclipse project or vanilla

            String[] files = projectDir.list();

            for( String file : files )
            {
                if( IProjectDescription.DESCRIPTION_FILE_NAME.equals( file ) )
                {
                    projectRecord = new ProjectRecord( new File( projectDir, file ) );
                }
            }

            if( projectRecord == null )
            {
                projectRecord = new ProjectRecord( projectDir );
            }
        }

        return projectRecord;
    }

    public static String getRelativePathFromDocroot( IProject project, String path )
    {
        IFolder docroot = CoreUtil.getDefaultDocrootFolder( project );

        IPath pathValue = new Path( path );

        IPath relativePath = pathValue.makeRelativeTo( docroot.getFullPath() );

        String retval = relativePath.toPortableString();

        return retval.startsWith( "/" ) ? retval : "/" + retval;
    }

    public static IPackageFragmentRoot[] getSourceContainers( IProject project )
    {
        IJavaProject jProject = JavaCore.create( project );

        if( jProject == null )
        {
            return new IPackageFragmentRoot[0];
        }

        List<IPackageFragmentRoot> list = new ArrayList<IPackageFragmentRoot>();
        IVirtualComponent vc = ComponentCore.createComponent( project );
        IPackageFragmentRoot[] roots;

        try
        {
            roots = jProject.getPackageFragmentRoots();

            for( int i = 0; i < roots.length; i++ )
            {
                if( roots[i].getKind() != IPackageFragmentRoot.K_SOURCE )
                {
                    continue;
                }

                IResource resource = roots[i].getResource();

                if( null != resource )
                {
                    IVirtualResource[] vResources = ComponentCore.createResources( resource );
                    boolean found = false;

                    for( int j = 0; !found && j < vResources.length; j++ )
                    {
                        if( vResources[j].getComponent().equals( vc ) )
                        {
                            if( !list.contains( roots[i] ) )
                            {
                                list.add( roots[i] );
                            }

                            found = true;
                        }
                    }
                }
            }

            if( list.size() == 0 )
            {
                for( IPackageFragmentRoot root : roots )
                {
                    if( root.getKind() == IPackageFragmentRoot.K_SOURCE )
                    {
                        if( !list.contains( root ) )
                        {
                            list.add( root );
                        }
                    }
                }
            }
        }
        catch( JavaModelException e )
        {
            ProjectCorePlugin.logError( e );
        }

        return list.toArray( new IPackageFragmentRoot[list.size()] );
    }

    public static IFolder[] getSourceFolders( IProject project )
    {
        List<IFolder> sourceFolders = new ArrayList<IFolder>();

        IPackageFragmentRoot[] sources = getSourceContainers( project );

        for( IPackageFragmentRoot source : sources )
        {
            if( source.getResource() instanceof IFolder )
            {
                sourceFolders.add( ( (IFolder) source.getResource() ) );
            }
        }

        return sourceFolders.toArray( new IFolder[sourceFolders.size()] );
    }

    public static boolean hasFacet( IProject project, IProjectFacet checkProjectFacet )
    {
        boolean retval = false;

        if( project == null || checkProjectFacet == null )
        {
            return retval;
        }

        try
        {
            IFacetedProject facetedProject = ProjectFacetsManager.create( project );

            if( facetedProject != null && checkProjectFacet != null )
            {
                for( IProjectFacetVersion facet : facetedProject.getProjectFacets() )
                {
                    IProjectFacet projectFacet = facet.getProjectFacet();

                    if( checkProjectFacet.equals( projectFacet ) )
                    {
                        retval = true;
                        break;
                    }
                }
            }
        }
        catch( CoreException e )
        {
        }

        return retval;
    }

    public static boolean hasFacet( IProject project, String facetId )
    {
        return hasFacet( project, ProjectFacetsManager.getProjectFacet( facetId ) );
    }

    public static boolean hasProperty( IDataModel model, String propertyName )
    {
        boolean retval = false;

        if( model == null || CoreUtil.isNullOrEmpty( propertyName ) )
        {
            return retval;
        }

        for( Object property : model.getAllProperties() )
        {
            if( propertyName.equals( property ) )
            {
                retval = true;
                break;
            }
        }

        return retval;
    }

    public static IProject importProject(
        ProjectRecord projectRecord, IRuntime runtime, String sdkLocation, IProgressMonitor monitor )
        throws CoreException
    {
        IProject project = null;

        if( projectRecord.projectSystemFile != null )
        {
            try
            {
                project = createExistingProject( projectRecord, runtime, sdkLocation, monitor );
            }
            catch( CoreException e )
            {
                throw new CoreException( ProjectCorePlugin.createErrorStatus( e ) );
            }
        }
        else if( projectRecord.liferayProjectDir != null )
        {
            try
            {
                project = createNewProject( projectRecord, runtime, sdkLocation, monitor );
            }
            catch( CoreException e )
            {
                throw new CoreException( ProjectCorePlugin.createErrorStatus( e ) );
            }
        }

        return project;
    }

    public static boolean isDynamicWebFacet( IProjectFacet facet )
    {
        return facet != null && facet.getId().equals( IModuleConstants.JST_WEB_MODULE );
    }

    public static boolean isDynamicWebFacet( IProjectFacetVersion facetVersion )
    {
        return facetVersion != null && isDynamicWebFacet( facetVersion.getProjectFacet() );
    }

    public static boolean isExtProject( IProject project )
    {
        return hasFacet( project, IPluginFacetConstants.LIFERAY_EXT_PROJECT_FACET );
    }

    public static boolean isHookProject( IProject project )
    {
        return hasFacet( project, IPluginFacetConstants.LIFERAY_HOOK_PROJECT_FACET );
    }

    public static boolean isJavaFacet( IProjectFacet facet )
    {
        return facet != null &&
            ( facet.getId().equals( JavaFacet.ID ) || facet.getId().equals( IModuleConstants.JST_JAVA ) );
    }

    public static boolean isJavaFacet( IProjectFacetVersion facetVersion )
    {
        return facetVersion != null && isJavaFacet( facetVersion.getProjectFacet() );
    }

    public static boolean isLayoutTplProject( IProject project )
    {
        return hasFacet( project, IPluginFacetConstants.LIFERAY_LAYOUTTPL_FACET_ID );
    }

    public static boolean isLiferayFacet( IProjectFacet projectFacet )
    {
        return ProjectCorePlugin.getProjectDefinition( projectFacet ) != null;
    }

    public static boolean isLiferayFacet( IProjectFacetVersion projectFacetVersion )
    {
        return projectFacetVersion != null &&
            ProjectCorePlugin.getProjectDefinition( projectFacetVersion.getProjectFacet() ) != null;
    }

    public static boolean isLiferayPluginType( String type )
    {
        return type != null &&
            ( ISDKConstants.PORTLET_PLUGIN_PROJECT_SUFFIX.endsWith( type ) ||
                ISDKConstants.HOOK_PLUGIN_PROJECT_SUFFIX.endsWith( type ) ||
                ISDKConstants.EXT_PLUGIN_PROJECT_SUFFIX.endsWith( type ) ||
                ISDKConstants.LAYOUTTPL_PLUGIN_PROJECT_SUFFIX.endsWith( type ) ||
                ISDKConstants.THEME_PLUGIN_PROJECT_SUFFIX.endsWith( type ) );
    }

    public static boolean isLiferayProject( IFolder folder )
    {
        return folder != null && folder.exists() && folder.getRawLocation() != null &&
            isLiferayProjectDir( folder.getRawLocation().toFile() );
    }

    public static boolean isLiferayProject( IProject project )
    {
        boolean retval = false;

        if( project == null )
        {
            return retval;
        }

        try
        {
            IFacetedProject facetedProject = ProjectFacetsManager.create( project );

            if( facetedProject != null )
            {
                for( IProjectFacetVersion facet : facetedProject.getProjectFacets() )
                {
                    IProjectFacet projectFacet = facet.getProjectFacet();
                    IProjectDefinition projectDefinition = ProjectCorePlugin.getProjectDefinition( projectFacet );

                    if( projectDefinition != null )
                    {
                        retval = true;
                        break;
                    }
                }
            }
        }
        catch( Exception e )
        {
        }

        return retval;
    }

    public static boolean isLiferayProjectDir( File file )
    {
        // TODO IDE-110 IDE-648
        if( file.isDirectory() && isValidLiferayProjectDir( file ) )
        {
            // check for build.xml and docroot
            File[] contents = file.listFiles();

            boolean hasBuildXml = false;

            boolean hasDocroot = false;

            for( File content : contents )
            {
                if( content.getName().equals( "build.xml" ) )
                {
                    hasBuildXml = true;

                    continue;
                }

                if( content.getName().equals( "docroot" ) )
                {
                    hasDocroot = true;

                    continue;
                }
            }

            if( hasBuildXml && hasDocroot )
            {
                return true;
            }
        }

        return false;
    }

    public static boolean isParent( IFolder folder, IResource resource )
    {
        if( folder == null || resource == null )
        {
            return false;
        }

        if( resource.getParent() != null && resource.getParent().equals( folder ) )
        {
            return true;
        }
        else
        {
            boolean retval = isParent( folder, resource.getParent() );

            if( retval == true )
            {
                return true;
            }
        }

        return false;
    }

    public static boolean isPortletProject( IProject project )
    {
        return hasFacet( project, IPluginFacetConstants.LIFERAY_PORTLET_PROJECT_FACET );
    }

    public static boolean isSDKProject( IProject project )
    {
        if( project == null || ( !project.exists() ) || ( !project.isAccessible() ) )
        {
            return false;
        }

        return SDKUtil.isValidSDKLocation( project.getLocation().toOSString() );
    }

    public static boolean isThemeProject( IProject project )
    {
        return hasFacet( project, IPluginFacetConstants.LIFERAY_THEME_FACET_ID );
    }

    public static boolean isValidLiferayProjectDir( File dir )
    {
        String name = dir.getName();

        if( name.endsWith( ISDKConstants.PORTLET_PLUGIN_PROJECT_SUFFIX ) ||
            name.endsWith( ISDKConstants.EXT_PLUGIN_PROJECT_SUFFIX ) ||
            name.endsWith( ISDKConstants.HOOK_PLUGIN_PROJECT_SUFFIX ) ||
            name.endsWith( ISDKConstants.THEME_PLUGIN_PROJECT_SUFFIX ) ||
            name.endsWith( ISDKConstants.LAYOUTTPL_PLUGIN_PROJECT_SUFFIX ) )
        {
            return true;
        }

        return false;
    }

    public static String removePluginSuffix( String string )
    {
        if( string == null )
        {
            return null;
        }

        String regex = null;

        if( string.endsWith( ISDKConstants.PORTLET_PLUGIN_PROJECT_SUFFIX ) )
        {
            regex = ISDKConstants.PORTLET_PLUGIN_PROJECT_SUFFIX + "$";
        }
        else if( string.endsWith( ISDKConstants.HOOK_PLUGIN_PROJECT_SUFFIX ) )
        {
            regex = ISDKConstants.HOOK_PLUGIN_PROJECT_SUFFIX + "$";
        }
        else if( string.endsWith( ISDKConstants.EXT_PLUGIN_PROJECT_SUFFIX ) )
        {
            regex = ISDKConstants.EXT_PLUGIN_PROJECT_SUFFIX + "$";
        }
        else if( string.endsWith( ISDKConstants.LAYOUTTPL_PLUGIN_PROJECT_SUFFIX ) )
        {
            regex = ISDKConstants.LAYOUTTPL_PLUGIN_PROJECT_SUFFIX + "$";
        }
        else if( string.endsWith( ISDKConstants.THEME_PLUGIN_PROJECT_SUFFIX ) )
        {
            regex = ISDKConstants.THEME_PLUGIN_PROJECT_SUFFIX + "$";
        }
        else
        {
            return string;
        }

        return string.replaceFirst( regex, "" );
    }

    public static void setDefaultRuntime(IDataModel dataModel)
    {
        DataModelPropertyDescriptor[] validDescriptors =
            dataModel.getValidPropertyDescriptors( IFacetProjectCreationDataModelProperties.FACET_RUNTIME );

        for( DataModelPropertyDescriptor desc : validDescriptors ) 
		{
            Object runtime = desc.getPropertyValue();

            if( runtime instanceof BridgedRuntime && ServerUtil.isLiferayRuntime( (BridgedRuntime) runtime ) ) 
			{
                dataModel.setProperty( IFacetProjectCreationDataModelProperties.FACET_RUNTIME, runtime );
                break;
            }
        }
    }

    public static void setGenerateDD( IDataModel model, boolean generateDD )
    {
        IDataModel ddModel = null;

        if( hasProperty( model, IJ2EEFacetInstallDataModelProperties.GENERATE_DD ) )
        {
            ddModel = model;
        }
        else if( hasProperty( model, IFacetProjectCreationDataModelProperties.FACET_DM_MAP ) )
        {
            FacetDataModelMap map =
                (FacetDataModelMap) model.getProperty( IFacetProjectCreationDataModelProperties.FACET_DM_MAP );
            ddModel = map.getFacetDataModel( IJ2EEFacetConstants.DYNAMIC_WEB_FACET.getId() );
        }

        if( ddModel != null )
        {
            ddModel.setBooleanProperty( IJ2EEFacetInstallDataModelProperties.GENERATE_DD, generateDD );
        }
    }

}
