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
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.artifact.filter.StrictPatternExcludesArtifactFilter;
import org.apache.maven.shared.artifact.filter.StrictPatternIncludesArtifactFilter;
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
     * @parameter default-value="${project.build.directory}/jarjar"
     * @required
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
    private String input;

    /**
     * Where to put the JarJar'd classes.
     * 
     * @parameter
     */
    private String output;

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

            if ( null == input && null != project.getArtifact() )
            {
                input = project.getArtifact().getFile().getAbsolutePath();
            }
            if ( "{classes}".equals( input ) )
            {
                input = project.getBuild().getOutputDirectory();
            }
            if ( "{test-classes}".equals( input ) )
            {
                input = project.getBuild().getTestOutputDirectory();
            }
            if ( null == input || !new File( input ).exists() )
            {
                getLog().info( "Nothing to process" );
                return;
            }

            if ( null == output )
            {
                output = input;
            }

            final File inputFile = new File( input );
            final File outputFile = new File( output );

            final File backupFile = new File( outputFile.getParentFile(), "original-" + outputFile.getName() );
            if ( backupFile.isDirectory() && backupFile.list().length == 0 )
            {
                backupFile.delete();
            }
            if ( inputFile.equals( outputFile ) && backupFile.exists() )
            {
                getLog().info( "Already processed" );
                return;
            }

            // SETUP JARJAR

            final MainProcessor processor = new MainProcessor( rules, getLog().isDebugEnabled(), skipManifest );
            final AndArtifactFilter filter = new AndArtifactFilter();
            if ( null != includes )
            {
                filter.add( new StrictPatternIncludesArtifactFilter( includes ) );
            }
            if ( null != excludes )
            {
                filter.add( new StrictPatternExcludesArtifactFilter( excludes ) );
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
                        archiver.addArchivedFileSet( a.getFile(), null, new String[] { "META-INF/MANIFEST.MF" } );
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
            throw new MojoExecutionException( "Unable to JarJar: " + input, e );
        }
    }
}
