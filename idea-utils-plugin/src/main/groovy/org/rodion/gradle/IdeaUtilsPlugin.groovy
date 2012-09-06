/*
 *  Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
 */

package org.rodion.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.XmlProvider

/**
 * <p>Created: 6/7/12 5:00 PM</p>
 * @author rodion
 */
class IdeaUtilsPlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        project.plugins.apply(IdeaUtilsBasePlugin)
        addRunConfigurationsToProjectIpr(project)
        addVcsSettingsToProjectIpr(project)
        addCopyrightSettingsToProjectIpr(project)
        addMiscellaneousSettingsToProjectIpr(project)
    }

    def addMiscellaneousSettingsToProjectIpr(Project project) {
        project.idea.project.ipr.withXml { XmlProvider provider ->
            MiscExtension ext = project.idea.project.extensions.findByName(IdeaUtilsBasePlugin.MISC_EXTENSION_NAME)
            if (ext.dynamicClasspath) {
                def dynamicCp = provider.node.appendNode('component')
                dynamicCp.@name = 'PropertiesComponent'
                dynamicCp.appendNode('property', [name: 'dynamic.classpath', value: 'true'])
            }
        }
    }

    def addCopyrightSettingsToProjectIpr(Project project) {
        project.idea.project.ipr.withXml { XmlProvider provider ->
            CopyrightExtension ext = project.idea.project.extensions.findByName(IdeaUtilsBasePlugin.COPYRIGHT_EXTENSION_NAME)
            if (ext.licenseSpecified) {
                if (ext.name == null) {
                    throw new IdeaUtilsPluginException("Required 'name' field nas not been specified. Please set "
                            + "idea.project.${IdeaUtilsBasePlugin.COPYRIGHT_EXTENSION_NAME}.name field to any String value.")
                }
                if (ext.license == null) {
                    throw new IdeaUtilsPluginException("Required 'license' field nas not been specified. Please set "
                            + "idea.project.${IdeaUtilsBasePlugin.COPYRIGHT_EXTENSION_NAME}.license field to path "
                            + "to a file contaning license content.")
                }

                def comp = provider.node.component.find { it.@name == 'CopyrightManager' }
                comp.@default = ext.name
                def copyright = comp.appendNode('copyright')
                copyright.appendNode('option', [name: 'notice', value: ext.license.text])
                ['keyword': 'Copyright',
                        'allowReplaceKeyword': '',
                        'myName': ext.name,
                        'myLocal': 'true'].each { name, value ->
                    copyright.appendNode('option', ['name': name, 'value': value])
                }

            }
        }
    }

    def addVcsSettingsToProjectIpr(Project project) {
        project.idea.project.ipr.withXml { XmlProvider provider ->
            VcsExtension vcsSettings = project.idea.project.extensions.findByName(IdeaUtilsBasePlugin.VCS_EXTENSION_NAME)
            if (vcsSettings.vcs != null) {
                def mapping = provider.node.component.find { it.@name == 'VcsDirectoryMappings' }.mapping
                mapping.@vcs = vcsSettings.vcs
                mapping.@directory = vcsSettings.directory
            }
        }
    }

    def addRunConfigurationsToProjectIpr(Project project) {
        def runConfigs = project.idea.project.extensions.findByName(IdeaUtilsBasePlugin.RUN_CONFIG_EXTENSION_NAME)
        project.idea.project.ipr.withXml { XmlProvider provider ->
            //All run configurations are stored inside a dedicated
            //ProjectRunConfigurationManager component
            def runConfigComp = provider.node.appendNode('component')
            runConfigComp.@name = 'ProjectRunConfigurationManager'
            runConfigs.each { RunConfiguration config ->
                //Check required fields!
                if (config.mainClass == null) {
                    throw new IdeaUtilsPluginException("Required 'mainClass' field has not been specified. Please set "
                            + "idea.project.${IdeaUtilsBasePlugin.RUN_CONFIG_EXTENSION_NAME}.${config.configName}.mainClass "
                            + "to an executable class name, e.g. mainClass = 'com.example.Main'")
                }
                def configurationNode = runConfigComp.appendNode('configuration',
                        ["default": config.isDefault,
                                name: config.name,
                                type: config.getRunConfigType().internalType,
                                factoryName: config.getRunConfigType().name()])
                configureOptions(config, configurationNode)
            }
        }
    }

    def configureOptions(RunConfiguration config, Node configurationNode) {
        configurationNode.appendNode("extension", [name: "coverage",
                enabled: "false",
                merge: "false",
                runner: "idea"])
        ["MAIN_CLASS_NAME": config.mainClass,
                "VM_PARAMETERS": config.vmOptions,
                "WORKING_DIRECTORY": config.workingDirectory.absolutePath].each { name, value ->
            configurationNode.appendNode("option", ["name": name, "value": value])
        }
        configurationNode.appendNode("module", [name: config.useModuleClasspath])
        configurationNode.appendNode("envs")
        configurationNode.appendNode("method")
        switch (config.runConfigType) {
            case RunConfigType.Application:
                configureApplicationOptions(config, configurationNode)
                break;
            case RunConfigType.JUnit:
                configureJUnitOptions(config, configurationNode)
                break;
            default:
                throw new IdeaUtilsPluginException("Unexpected run configuration type: " + config.runConfigType)
        }
    }

    def configureApplicationOptions(RunConfiguration config, Node configurationNode) {
        ["PROGRAM_PARAMETERS": config.programArguments].each { name, value ->
            configurationNode.appendNode("option", ["name": name, "value": value])
        }
    }

    def configureJUnitOptions(RunConfiguration config, Node configurationNode) {
        ["TEST_OBJECT": "class"].each { name, value ->
            configurationNode.appendNode("option", ["name": name, "value": value])
        }
        configurationNode.appendNode("option").appendNode("value", [defaultName: "moduleWithDependencies"])
        configurationNode.appendNode("patterns")
    }
}