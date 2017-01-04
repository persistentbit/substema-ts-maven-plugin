package com.persistentbit.substema.mavenplugin;

import com.persistentbit.core.collections.PList;
import com.persistentbit.core.collections.PStream;
import com.persistentbit.substema.compiler.SubstemaCompiler;
import com.persistentbit.substema.compiler.values.RSubstema;
import com.persistentbit.substema.dependencies.DependencySupplier;
import com.persistentbit.substema.dependencies.SupplierDef;
import com.persistentbit.substema.dependencies.SupplierType;
import com.persistentbit.substema.javagen.JavaGenOptions;
import com.persistentbit.substema.javagen.SubstemaJavaGen;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.List;

/*
 * Generate packages from a ROD file
 *
 * @goal generate-packages
 * @phase generate-packages
 *
 * @description Generate packages from a ROD file
 */
@Mojo(
	name = "generate-packages",
	defaultPhase = LifecyclePhase.GENERATE_SOURCES,
	requiresDependencyResolution = ResolutionScope.COMPILE_PLUS_RUNTIME
)
public class SubstemaTsMojo extends AbstractMojo{

	@Parameter(property = "project", required = true, readonly = true)
	MavenProject project;


	@Parameter(defaultValue = "target/generated-sources/substema", required = true)
	File outputDirectory;

	@Parameter(defaultValue = "src/main/resources", required = true)
	File resourcesDirectory;

	/*
	 * Sources
	 *
	 * @parameter
	 * @required
	 */
	@Parameter(name = "packages", required = true)
	List<String> packages;


	public void execute() throws MojoExecutionException, MojoFailureException {
		try {
			DependencySupplier dependencySupplier = createDependencySupplier();

			//Compile the source
			SubstemaCompiler compiler = new SubstemaCompiler(dependencySupplier);
			PList<RSubstema> substemas = PList.from(packages)
				.map(p -> compiler.compile(p).orElseThrow());

			substemas.forEach(ss -> getLog().info(ss.toString()));

			//Create output directory
			if(!outputDirectory.exists()) {
				if(outputDirectory.mkdirs() == false) {
					throw new MojoExecutionException("Can't create output folder " + outputDirectory.getAbsolutePath());
				}
			}
			project.addCompileSourceRoot(outputDirectory.getAbsolutePath());

			// GENERATE JAVA

			JavaGenOptions genOptions = new JavaGenOptions(true, true);

			substemas.forEach(ss ->
				SubstemaJavaGen.generateAndWriteToFiles(compiler, genOptions, ss, outputDirectory)
			);
		} catch(Exception e) {
			getLog().error("General error", e);
			throw new MojoExecutionException("Error while generating code:" + e.getMessage(), e);
		}


	}

	private DependencySupplier createDependencySupplier() throws MojoExecutionException {

		getLog().info("Compiling Substemas...");
		PList<SupplierDef> supplierDefs = PList.empty();
		try {
			if(resourcesDirectory.exists()) {
				getLog().info("Adding Dependency Supplier " + SupplierType.folder + " , " + resourcesDirectory
					.getAbsolutePath());
				supplierDefs =
					supplierDefs.plus(new SupplierDef(SupplierType.folder, resourcesDirectory.getAbsolutePath()));

			}
			List<String> classPathElements = project.getCompileClasspathElements();
			if(classPathElements != null) {
				supplierDefs = supplierDefs.plusAll(PStream.from(classPathElements).map(s -> {
					File f = new File(s);
					if(f.exists()) {
						SupplierType type = f.isDirectory() ? SupplierType.folder : SupplierType.archive;
						getLog().info("Adding Dependency Supplier " + type + " , " + f.getAbsolutePath());
						return new SupplierDef(type, f.getAbsolutePath());
					}
					else {
						return null;
					}
				}).filterNulls());
			}

		} catch(Exception e) {
			throw new MojoExecutionException("Error building dependencyList", e);
		}

		return new DependencySupplier(supplierDefs);
	}

}
