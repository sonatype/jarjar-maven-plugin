/**
 * Copyright 2007 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tonicsystems.jarjar;

import java.io.File;
import java.util.List;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.AndArtifactFilter;
import org.apache.maven.artifact.resolver.filter.ExcludesArtifactFilter;
import org.apache.maven.artifact.resolver.filter.IncludesArtifactFilter;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.FileUtils;

import com.tonicsystems.jarjar.util.StandaloneJarProcessor;

/**
 * Repackage dependencies and embed them into the final artifact.
 * 
 * @goal jarjar
 * @phase package
 * @requiresDependencyResolution test
 */
public class JarJarMojo
    extends AbstractMojo
{
    /**
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * List of JarJar rules.
     * 
     * @parameter
     * @required
     */
    private List<PatternElement> rules;

    /**
     * Where to put the JarJar'd archive.
     * 
     * @parameter
     */
    private File outputFile;

    /**
     * List of "groupId:artifactId" dependencies to include.
     * 
     * @parameter
     */
    private List<String> includes;

    /**
     * List of "groupId:artifactId" dependencies to exclude.
     * 
     * @parameter
     */
    private List<String> excludes;

    /**
     * When true, don't JarJar the manifest.
     * 
     * @parameter
     */
    private boolean skipManifest;

    /**
     * @component
     */
    private ArchiverManager archiverManager;

    @SuppressWarnings( "unchecked" )
    public void execute()
        throws MojoExecutionException
    {
        // SETUP JARJAR

        final MainProcessor processor = new MainProcessor( rules, getLog().isDebugEnabled(), skipManifest );
        final AndArtifactFilter filter = new AndArtifactFilter();
        if ( null != includes )
        {
            filter.add( new IncludesArtifactFilter( includes ) );
        }
        if ( null != excludes )
        {
            filter.add( new ExcludesArtifactFilter( excludes ) );
        }

        final File file = project.getArtifact().getFile();
        final File orig = new File( file.getParentFile(), "original-" + file.getName() );
        final File uber = new File( file.getParentFile(), "uber-" + file.getName() );

        try
        {
            // BUILD UBER-JAR OF ARTIFACT + DEPENDENCIES

            final Archiver archiver = archiverManager.getArchiver( "zip" );

            archiver.setDestFile( uber );
            archiver.setIncludeEmptyDirs( false );
            archiver.addArchivedFileSet( file );

            for ( final Artifact a : (Set<Artifact>) project.getArtifacts() )
            {
                if ( filter.include( a ) )
                {
                    try
                    {
                        archiver.addArchivedFileSet( a.getFile() );
                    }
                    catch ( final Throwable e )
                    {
                        getLog().info( "Ignoring: " + a );
                        getLog().debug( e );
                    }
                }
            }

            archiver.createArchive();

            // BACKUP PREVIOUS ARTIFACT

            if ( null == outputFile )
            {
                FileUtils.copyFile( file, orig );
                outputFile = file;
            }

            // JARJAR UBER-JAR

            getLog().info( "JarJar'ing to: " + outputFile );
            StandaloneJarProcessor.run( uber, outputFile, processor );
            processor.strip( outputFile );

            uber.delete();
        }
        catch ( final Throwable e )
        {
            throw new MojoExecutionException( e.toString() );
        }
    }
}
