/*
 * Copyright Amazon.com, Inc. or its affiliates. All Rights Reserved.
 * SPDX-License-Identifier: Apache-2.0.
 */
package software.amazon.smithy.swift.codegen

import software.amazon.smithy.build.FileManifest
import software.amazon.smithy.codegen.core.SymbolDependency
import software.amazon.smithy.utils.CodeWriter

fun writePackageManifest(settings: SwiftSettings, fileManifest: FileManifest, dependencies: List<SymbolDependency>, generateTestTarget: Boolean = false) {

    // filter duplicates in dependencies
    val distinctDependencies = dependencies.distinctBy { it.packageName }
    val writer = CodeWriter().apply {
        trimBlankLines()
        trimTrailingSpaces()
        setIndentText("    ")
    }

    writer.write("// swift-tools-version:${settings.swiftVersion}")
    writer.write("")
    writer.write("import PackageDescription")

    writer.openBlock("let package = Package(", ")") {
        writer.write("name: \"${settings.moduleName}\",")

        writer.openBlock("platforms: [", "],") {
            writer.write(".macOS(.v10_15), .iOS(.v13)")
        }

        writer.openBlock("products: [", "],") {
            writer.write(".library(name: \"${settings.moduleName}\", targets: [\"${settings.moduleName}\"])")
        }

        writer.openBlock("dependencies: [", "],") {
            for (dependency in distinctDependencies) {
                val dependencyURL = dependency.expectProperty("url", String::class.java)
                if (dependencyURL.take(4).toLowerCase().equals("http")) {
                    writer.openBlock(".package(", "),") {
                        writer.write("url: \"$dependencyURL\",")
                        writer.write("from: ${dependency.version}")
                    }
                } else {
                    writer.write(".package(path: \"$dependencyURL\"),")
                }
            }
        }

        writer.openBlock("targets: [", "]") {
            writer.openBlock(".target(", "),") {
                writer.write("name: \"${settings.moduleName}\",")
                writer.openBlock("dependencies: [", "],") {
                    for (dependency in distinctDependencies) {
                        writer.openBlock(".product(", "),") {
                            writer.write("name: \"${dependency.packageName}\",")
                            writer.write("package: \"${dependency.expectProperty("swiftPackageName", String::class.java)}\"")
                        }
                    }
                }
                writer.write("path: \"./${settings.moduleName}\"")
            }
            if (generateTestTarget) {
                writer.openBlock(".testTarget(", ")") {
                    writer.write("name: \"${settings.moduleName}Tests\",")
                    writer.openBlock("dependencies: [", "],") {
                        writer.write("\$S,", settings.moduleName)
                        writer.write(".product(name: \"SmithyTestUtil\", package: \"ClientRuntime\")")
                    }
                    writer.write("path: \"./${settings.moduleName}Tests\"")
                }
            }
        }
    }

    val contents = writer.toString()
    fileManifest.writeFile("Package.swift", contents)
}