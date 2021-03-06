package com.github.bringking.maven.requirejs;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.filtering.MavenFileFilter;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.mozilla.javascript.ErrorReporter;
import org.mozilla.javascript.EvaluatorException;

/**
 * Mojo for running r.js optimization.
 *
 * @goal optimize
 * @phase process-classes
 */
public class OptimizeMojo extends AbstractMojo {

    /**
     * @component role="org.apache.maven.shared.filtering.MavenFileFilter"
     * role-hint="default"
     * @required
     */
    private MavenFileFilter mavenFileFilter;

    /**
     * @parameter default-value="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * @parameter default-value="${project.build.directory}"
     * @required
     * @readonly
     */
    private File buildDirectory;

    /**
     * @parameter expression="${session}"
     * @required
     * @readonly
     */
    protected MavenSession session;

    /**
     * Path to optimizer script.
     *
     * @parameter
     */
    private File optimizerFile;

    /**
     * Path to optimizer json config.
     *
     * @parameter
     * @required
     */
    private File configFile;

    /**
     * Whether or not the config file should
     * be maven filtered for token replacement.
     *
     * @parameter default-value=false
     */
    private boolean filterConfig;

    /**
     * Skip optimization when this parameter is true.
     *
     * @parameter expression="${requirejs.optimize.skip}" default-value=false
     */
    private boolean skip;

    /**
     * Defines which javascript engine to use. Possible values: rhino or nodejs.
     *
     * @parameter expression="${requirejs.optimize.runner}" default-value=nodejs
     */
    private String runner = "nodejs";

    /**
     * Defines the location of the NodeJS executable to use.
     *
     * @parameter
     */
    private String nodeExecutable;

    /**
     * Defines the command line parameters to pass to the optimizer
     *
     * @parameter
     */
    private String[] optimizerParameters;

    /**
     * Optimize files.
     *
     * @throws MojoExecutionException if there is a problem optimizing files.
     */
    public void execute() throws MojoExecutionException {
        if (skip) {
            getLog().info("Optimization is skipped.");
            return;
        }

        Runner runner;
        String nodeCommand = getNodeCommand();
        
        if (nodeCommand != null) {
            getLog().info("Running with Node @ " + nodeCommand);
            runner = new NodeJsRunner(nodeCommand);
        } else {
            getLog().info("Node not detected. Falling back to rhino");
            runner = new RhinoRunner();
        }

        try {
            Optimizer builder = new Optimizer();
            ErrorReporter reporter = new MojoErrorReporter(getLog(), true);

            if (optimizerFile != null) {


                if (this.optimizerParameters != null) {
                    builder.optimize(createBuildProfile(), optimizerFile, reporter, runner, this.optimizerParameters);
                } else builder.optimize(createBuildProfile(), optimizerFile, reporter, runner);

            } else {

                if (this.optimizerParameters != null) {
                    builder.optimize(createBuildProfile(), reporter, runner, this.optimizerParameters);
                } else builder.optimize(createBuildProfile(), reporter, runner);


            }
        } catch (IOException e) {
            throw new MojoExecutionException("Failed to read r.js", e);
        } catch (EvaluatorException e) {
            throw new MojoExecutionException("Failed to execute r.js", e);
        } catch (OptimizationException e) {
            throw new MojoExecutionException("r.js exited with an error.");
        }
    }
    /**
     * Returns the node command if node is available and it is the runner which should be used.
     * 
     * @return the command or <code>null</code>
     */
    private String getNodeCommand() {
        if ("nodejs".equalsIgnoreCase(runner)) {
            return getNodeJsPath();
        }
        
        return null;
    }

	@SuppressWarnings("rawtypes")
    public Map getPluginContext() {
        return super.getPluginContext();
    }

    @SuppressWarnings("rawtypes")
    private File createBuildProfile() throws MojoExecutionException {
        if (filterConfig) {
            File filteredConfig;

            try {
                File profileDir = new File(buildDirectory, "requirejs-config/");
                profileDir.mkdirs();
                filteredConfig = new File(profileDir, "filtered-build.js");
                if (!filteredConfig.exists()) {
                    filteredConfig.createNewFile();
                }
                mavenFileFilter.copyFile(configFile, filteredConfig, true, project, new ArrayList(), true, "UTF8", session);
            } catch (IOException e) {
                throw new MojoExecutionException("Error creating filtered build file.", e);
            } catch (MavenFilteringException e) {
                throw new MojoExecutionException("Error filtering config file.", e);
            }

            return filteredConfig;
        } else {
            return configFile;
        }
    }

    private String getNodeJsPath() {
        if (nodeExecutable != null) {
            return nodeExecutable;
        } else {
            return NodeJsRunner.detectNodeCommand();
        }
    }
}
