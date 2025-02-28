package edu.wpi.first.gradlerio.wpi.java;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.apache.log4j.Logger;
import org.gradle.api.Project;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import edu.wpi.first.gradlerio.wpi.WPIVersionsExtension;
import edu.wpi.first.gradlerio.wpi.dependencies.WPIDependenciesPlugin;
import edu.wpi.first.gradlerio.wpi.dependencies.WPIVendorDepsExtension;
import edu.wpi.first.gradlerio.wpi.dependencies.WPIVendorDepsExtension.JavaArtifact;
import edu.wpi.first.gradlerio.wpi.dependencies.WPIVendorDepsExtension.JniArtifact;
import edu.wpi.first.gradlerio.wpi.dependencies.WPIVendorDepsExtension.JsonDependency;

public class WPIJavaVendorDepsExtension {
    private final WPIVendorDepsExtension vendorDeps;
    private final ProviderFactory providerFactory;
    private final WPIVersionsExtension versions;
    private final Project project;

    @Inject
    public WPIJavaVendorDepsExtension(WPIVendorDepsExtension vendorDeps, WPIVersionsExtension versions, ProviderFactory providerFactory, Project project) {
        this.vendorDeps = vendorDeps;
        this.providerFactory = providerFactory;
        this.versions = versions;
        this.project = project;
    }

    public List<Provider<String>> java(String... ignore) {
        return vendorDeps.getDependenciesMap().entrySet().stream().map(x -> x.getValue()).filter(x -> !WPIVendorDepsExtension.isIgnored(ignore, x))
                .map(x -> List.of(x.javaDependencies)).flatMap(List<JavaArtifact>::stream).map(art -> {
                    String baseId = art.groupId + ":" + art.artifactId;
                    Callable<String> cbl = () -> baseId + ":" + WPIVendorDepsExtension.getVersion(art.version, providerFactory, versions);

                    try {
                        project.getDependencies().getComponents().withModule(baseId, details -> {
                            details.allVariants(varMeta -> {
                                varMeta.withDependencies(col -> {
                                    col.removeIf(item -> item.getGroup().startsWith("edu.wpi.first"));
                                });
                            });
                        });
                    } catch (Exception ex) {
                        Logger logger = Logger.getLogger(this.getClass());
                        logger.warn("Issue setting component metadata for " + baseId + ". Build could have issues with incorrect transitive dependencies.");
                        logger.warn("Please create an issue at https://github.com/wpilibsuite/allwpilib with this message so we can investigate");
                    }

                    return providerFactory.provider(cbl);
                }).collect(Collectors.toList());
    }

    public List<Provider<String>> jniRelease(String platform, String... ignore) {
        return jniInternal(false, platform, ignore);
    }

    public List<Provider<String>> jniDebug(String platform, String... ignore) {
        return jniInternal(true, platform, ignore);
    }

    private List<Provider<String>> jniInternal(boolean debug, String platform, String... ignore) {

        List<Provider<String>> deps = new ArrayList<>();

        for (JsonDependency dep : vendorDeps.getDependenciesMap().values()) {
            if (!WPIVendorDepsExtension.isIgnored(ignore, dep)) {
                for (JniArtifact jni : dep.jniDependencies) {
                    boolean applies = Arrays.asList(jni.validPlatforms).contains(platform);
                    if (!applies && !jni.skipInvalidPlatforms)
                        throw new WPIDependenciesPlugin.MissingJniDependencyException(dep.name, platform, jni);

                    if (applies) {
                        String debugString = debug ? "debug" : "";
                        Callable<String> cbl = () -> jni.groupId + ":" + jni.artifactId + ":" + WPIVendorDepsExtension.getVersion(jni.version, providerFactory, versions) + ":"
                                + platform + debugString + "@" + (jni.isJar ? "jar" : "zip");
                        deps.add(providerFactory.provider(cbl));
                    }
                }
            }
        }
        return deps;
    }
}
