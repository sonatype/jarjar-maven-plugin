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
import org.codehaus.plexus.archiver.UnArchiver;
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
     * @parameter expression="${project.build.directory}/jarjar"
     * @required
     * @readonly
     */
    private File workingDirectory;

    /**
     * List of JarJar rules.
     * 
     * @parameter
     * @required
     */
    private List<PatternElement> rules;

    /**
     * Where to get the original classes.
     * 
     * @parameter
     */
    private File inputFile;

    /**
     * Where to put the JarJar'd classes.
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
        try
        {
            // VALIDATE INPUT / OUTPUT

            if ( null == inputFile )
            {
                if ( null != project.getArtifact() )
                {
                    inputFile = project.getArtifact().getFile();
                }
                if ( null == inputFile )
                {
                    getLog().info( "Nothing to process" );
                    return;
                }
            }
            if ( null == outputFile )
            {
                outputFile = inputFile;
            }

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

            // BUILD UBER-ZIP OF ARTIFACT + DEPENDENCIES

            getLog().info( "Processing: " + inputFile );

            final File uberZip = new File( workingDirectory, "uber-" + inputFile.getName() );
            final Archiver archiver = archiverManager.getArchiver( "zip" );

            archiver.setDestFile( uberZip );
            archiver.setIncludeEmptyDirs( false );

            if ( inputFile.isDirectory() )
            {
                archiver.addDirectory( inputFile );
            }
            else
            {
                archiver.addArchivedFileSet( inputFile );
            }

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

            // JARJAR UBER-ZIP

            getLog().info( "JarJar'ing to: " + outputFile );

            final File hullZip = new File( workingDirectory, "hull-" + inputFile.getName() );

            StandaloneJarProcessor.run( uberZip, hullZip, processor );
            processor.strip( hullZip );

            final boolean toDirectory = outputFile.isDirectory() || !outputFile.exists() && inputFile.isDirectory();

            if ( inputFile.equals( outputFile ) )
            {
                try
                {
                    final File backupFile = new File( outputFile.getParentFile(), "original-" + outputFile.getName() );
                    getLog().info( "Original: " + backupFile );
                    FileUtils.rename( outputFile, backupFile );
                }
                catch ( final Throwable e )
                {
                    getLog().warn( e.toString() );
                }
            }

            if ( toDirectory )
            {
                outputFile.mkdirs();
                final UnArchiver unarchiver = archiverManager.getUnArchiver( "zip" );
                unarchiver.setDestDirectory( outputFile );
                unarchiver.setSourceFile( hullZip );
                unarchiver.extract();
            }
            else
            {
                FileUtils.copyFile( hullZip, outputFile );
            }
        }
        catch ( final Throwable e )
        {
            throw new MojoExecutionException( e.toString() );
        }
    }
}
