/*
 * Copyright 2020 Google LLC.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.cloud.tools.jib.cli.buildfile;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.google.cloud.tools.jib.api.InvalidImageReferenceException;
import com.google.cloud.tools.jib.api.Jib;
import com.google.cloud.tools.jib.api.JibContainerBuilder;
import com.google.cloud.tools.jib.api.buildplan.Platform;
import com.google.common.base.Charsets;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.stream.Collectors;
import org.apache.commons.text.StringSubstitutor;
import org.apache.commons.text.io.StringSubstitutorReader;

/** Class to convert BuildFiles to build container representations. */
public class BuildFiles {

  /** Read a build file from disk and apply templating parameters. */
  private static BuildFileSpec toBuildFileSpec(
      Path buildFilePath, Map<String, String> templateParameters) throws IOException {
    ObjectMapper yamlObjectMapper = new ObjectMapper(new YAMLFactory());
    StringSubstitutor templater =
        new StringSubstitutor(templateParameters).setEnableUndefinedVariableException(true);
    try (StringSubstitutorReader reader =
        new StringSubstitutorReader(
            Files.newBufferedReader(buildFilePath, Charsets.UTF_8), templater)) {
      return yamlObjectMapper.readValue(reader, BuildFileSpec.class);
    }
  }

  /**
   * Read a buildfile from disk and generate a JibContainerBuilder instance. All parsing of files
   * considers the directory the buildfile is located in as the working directory.
   *
   * @param projectRoot the root context directory of this build
   * @param buildFilePath a file containing the build definition
   * @param templateParameters a map of templating variables to apply on the file before parsing
   * @return a {@link JibContainerBuilder} generated from the contents of {@code buildFilePath}
   * @throws IOException if an I/O error occurs opening the file, or an error occurs while
   *     traversing files on the filesystem
   * @throws InvalidImageReferenceException if the baseImage reference can not be parsed
   */
  public static JibContainerBuilder toJibContainerBuilder(
      Path projectRoot, Path buildFilePath, Map<String, String> templateParameters)
      throws InvalidImageReferenceException, IOException {
    BuildFileSpec buildFile = toBuildFileSpec(buildFilePath, templateParameters);

    JibContainerBuilder containerBuilder;
    if (buildFile.getFrom().isPresent()) {
      BaseImageSpec from = buildFile.getFrom().get();
      containerBuilder = Jib.from(from.getImage());
      if (!from.getPlatforms().isEmpty()) {
        containerBuilder.setPlatforms(
            from.getPlatforms()
                .stream()
                .map(
                    platformSpec ->
                        new Platform(platformSpec.getArchitecture(), platformSpec.getOs()))
                .collect(Collectors.toSet()));
      }
    } else {
      containerBuilder = Jib.fromScratch();
    }

    buildFile.getCreationTime().ifPresent(containerBuilder::setCreationTime);
    buildFile.getFormat().ifPresent(containerBuilder::setFormat);
    containerBuilder.setEnvironment(buildFile.getEnvironment());
    containerBuilder.setLabels(buildFile.getLabels());
    containerBuilder.setVolumes(buildFile.getVolumes());
    containerBuilder.setExposedPorts(buildFile.getExposedPorts());
    buildFile.getUser().ifPresent(containerBuilder::setUser);
    buildFile.getWorkingDirectory().ifPresent(containerBuilder::setWorkingDirectory);
    buildFile.getEntrypoint().ifPresent(containerBuilder::setEntrypoint);
    buildFile.getCmd().ifPresent(containerBuilder::setProgramArguments);

    if (buildFile.getLayers().isPresent()) {
      containerBuilder.setFileEntriesLayers(
          Layers.toLayers(projectRoot, buildFile.getLayers().get()));
    }
    return containerBuilder;
  }
}
