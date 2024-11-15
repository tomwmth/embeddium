import net.minecraftforge.gradle.common.tasks.DownloadAssets
import net.minecraftforge.gradle.common.util.RunConfig
import org.embeddedt.embeddium.gradle.versioning.ProjectVersioner
import org.w3c.dom.Element

plugins {
    id("idea")
    id("maven-publish")
    id("net.minecraftforge.gradle") version("6.0.+")
    id("org.spongepowered.mixin") version("0.7.+")
    id("org.parchmentmc.librarian.forgegradle") version("1.+")
    id("embeddium-fabric-remapper")
}

operator fun String.invoke(): String {
    return (rootProject.properties[this] as String?)!!
}

tasks.withType<JavaCompile> {
    sourceCompatibility = JavaVersion.VERSION_17.majorVersion
    targetCompatibility = JavaVersion.VERSION_17.majorVersion
    options.encoding = "UTF-8"
}

version = getModVersion()
group = "maven_group"()
println("Embeddium: $version")

base {
    archivesName = "archives_base_name"()
}

val extraSourceSets = arrayOf("legacy", "compat")

sourceSets {
    val main = getByName("main")

    extraSourceSets.forEach {
        val sourceset = create(it)
        sourceset.apply {
            java {
                compileClasspath += main.compileClasspath
                compileClasspath += main.output
            }
        }
    }
}

repositories {
    mavenCentral()
    maven("https://maven.minecraftforge.net/")
    maven("https://maven.fabricmc.net")
    maven("https://api.modrinth.com/maven") {
        content {
            includeGroup("maven.modrinth")
        }
    }
    maven("https://cursemaven.com") {
        content {
            includeGroup("curse.maven")
        }
    }
}

jarJar.enable()

minecraft {
    if(rootProject.properties.containsKey("parchment_version")) {
        mappings("parchment", "parchment_version"())
    } else {
        mappings("official", "minecraft_version"())
    }
    copyIdeResources = true
    accessTransformer(file("src/main/resources/META-INF/accesstransformer.cfg"))
    runs {
        configureEach {
            workingDirectory(project.file("run"))

            property("forge.logging.console.level", "info")

            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", "${projectDir}/build/createSrgToMcp/output.srg")

            mods {
                create("embeddium") {
                    sources(sourceSets["main"])
                    extraSourceSets.forEach {
                        sources(sourceSets[it])
                    }
                }
            }
        }

        val client = create("client")


        fun configureGameTestRun(run: RunConfig) {
            run.parent(client)
            run.property("forge.enableGameTest", "true")
            run.property("embeddium.enableGameTest", "true")
        }

        create("gameTestClient") {
            configureGameTestRun(this)
        }

        create("gameTestCiClient") {
            configureGameTestRun(this)
            property("embeddium.runAutomatedTests", "true")
        }
    }
}

configurations {
    val runtimeOnlyNonPublishable = create("runtimeOnlyNonPublishable") {
        description = "Runtime only dependencies that are not published alongside the jar"
        isCanBeConsumed = false
        isCanBeResolved = false
    }
    runtimeClasspath.get().extendsFrom(runtimeOnlyNonPublishable)
}

val extraModsDir = "extra-mods-${"minecraft_version"()}"

repositories {
    flatDir {
        name = "extra-mods"
        dirs(file(extraModsDir))
    }
}

mixin {
    // MixinGradle Settings
    add(sourceSets["main"], "embeddium-refmap.json")
    config("embeddium.mixins.json")
}

fun DependencyHandlerScope.compatCompileOnly(dependency: Dependency) {
    "compatCompileOnly"(dependency)
}

fun fAPIModule(name: String): Dependency {
    return fabricApiModuleFinder.module(name, "fabric_version"())
}

dependencies {
    minecraft("net.minecraftforge:forge:${"minecraft_version"()}-${"forge_version"()}")

    // Mods
    compatCompileOnly(fg.deobf("curse.maven:immersiveengineering-231951:${"ie_fileid"()}"))
    "runtimeOnlyNonPublishable"(fg.deobf("curse.maven:modernfix-790626:${"mf_fileid"()}"))

    // Fabric API
    "fabricCompileOnly"(fAPIModule("fabric-api-base"))
    "fabricCompileOnly"(fAPIModule("fabric-renderer-api-v1"))
    "fabricCompileOnly"(fAPIModule("fabric-rendering-data-attachment-v1"))
    "fabricCompileOnly"(fAPIModule("fabric-renderer-indigo"))
    compileOnly("net.fabricmc:fabric-loader:${"fabric_loader_version"()}")

    annotationProcessor("net.fabricmc:sponge-mixin:0.12.5+mixin.0.8.5")

    compileOnly("io.github.llamalad7:mixinextras-common:0.3.5")
    annotationProcessor("io.github.llamalad7:mixinextras-common:0.3.5")

    compileOnly("org.projectlombok:lombok:1.18.30")
    annotationProcessor("org.projectlombok:lombok:1.18.30")

    implementation(jarJar("io.github.llamalad7:mixinextras-forge:0.3.5")) {
        jarJar.ranged(this, "[0.3.5,)")
    }

    // runtime remapping at home
    fileTree(extraModsDir) {
        include("*.jar") 
    }.files.forEach { extraModJar ->
        val basename = extraModJar.name.substring(0, extraModJar.name.length - ".jar".length)
        val versionSep = basename.lastIndexOf('-')
        assert(versionSep != -1)
        val artifactId = basename.substring(0, versionSep)
        val version = basename.substring(versionSep + 1)
        runtimeOnly(fg.deobf("extra-mods:$artifactId:$version"))
    }
}

tasks.processResources {
    inputs.property("version", "version"())

    filesMatching("META-INF/mods.toml") {
        expand("file" to mapOf("jarVersion" to inputs.properties["version"]))
    }
}

tasks.withType<JavaCompile> {
    options.release = 17
}

tasks.named<DownloadAssets>("downloadAssets") {
    // Try to work around asset download failures
    concurrentDownloads = 1
}


java {
    withSourcesJar()
}

tasks.named<Jar>("jar").configure {
    archiveClassifier = "slim"
}

tasks.jarJar {
    from("COPYING", "COPYING.LESSER", "README.md")

    extraSourceSets.forEach {
        from(sourceSets[it].output.classesDirs)
        from(sourceSets[it].output.resourcesDir)
    }

    finalizedBy("reobfJarJar")

    archiveClassifier = ""
}

tasks.named<Jar>("sourcesJar").configure {
    extraSourceSets.forEach {
        from(sourceSets[it].allJava)
    }
}

publishing {
    tasks.publish {
        dependsOn(tasks.build)
    }
    publications {
        this.create<MavenPublication>("mavenJava") {
            artifact(tasks.named("jarJar"))
            artifact(tasks.named("sourcesJar"))
            fg.component(this)
            pom {
                withXml {
                    // Workaround for NG only checking for net.minecraftforge group
                    val root = this.asElement()

                    val depsParent = (root.getElementsByTagName("dependencies").item(0) as Element)
                    val allDeps = depsParent.getElementsByTagName("dependency")

                    (0..allDeps.length).map { allDeps.item(it) }.filterIsInstance<Element>().filter {
                        val artifactId = it.getElementsByTagName("artifactId").item(0).textContent
                        val groupId = it.getElementsByTagName("groupId").item(0).textContent
                        (artifactId == "forge") && (groupId == "net.neoforged")
                    }.forEach {
                        depsParent.removeChild(it)
                    }
                }
            }
        }
    }

    repositories {
        maven("file://${System.getenv("local_maven")}")
    }
}

fun getModVersion(): String {
    return ProjectVersioner.computeVersion(project.projectDir, project.properties)
}
