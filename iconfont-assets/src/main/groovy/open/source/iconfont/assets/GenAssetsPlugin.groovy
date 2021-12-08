package open.source.iconfont.assets

import org.gradle.api.Plugin
import org.gradle.api.Project

import java.nio.file.Paths

class GenAssetsPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.extensions.create("iconfont", IconFontExtension.class)
        project.tasks.create("generateIconFontAssets", GenAssetsTask.class)
        project.tasks.findByName("preBuild").dependsOn("generateIconFontAssets")
        project.android.sourceSets.main.res.srcDirs += project.file(Paths.get("build", "generated", "main", "res"))
    }
}