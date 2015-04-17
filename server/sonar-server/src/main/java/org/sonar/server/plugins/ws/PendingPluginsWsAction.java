/*
 * SonarQube, open source software quality management tool.
 * Copyright (C) 2008-2014 SonarSource
 * mailto:contact AT sonarsource DOT com
 *
 * SonarQube is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * SonarQube is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.server.plugins.ws;

import com.google.common.io.Resources;
import org.sonar.api.server.ws.Request;
import org.sonar.api.server.ws.Response;
import org.sonar.api.server.ws.WebService;
import org.sonar.api.utils.text.JsonWriter;
import org.sonar.server.plugins.PluginDownloader;
import org.sonar.server.plugins.ServerPluginJarsInstaller;

import static com.google.common.collect.ImmutableSortedSet.copyOf;
import static java.lang.String.CASE_INSENSITIVE_ORDER;

/**
 * Implementation of the {@code pending} action for the Plugins WebService.
 */
public class PendingPluginsWsAction implements PluginsWsAction {

  private static final String ARRAY_INSTALLING = "installing";
  private static final String ARRAY_REMOVING = "removing";
  private static final String OBJECT_ARTIFACT = "artifact";
  private static final String PROPERTY_NAME = "name";

  private final PluginDownloader pluginDownloader;
  private final ServerPluginJarsInstaller serverPluginJarsInstaller;

  public PendingPluginsWsAction(PluginDownloader pluginDownloader, ServerPluginJarsInstaller serverPluginJarsInstaller) {
    this.pluginDownloader = pluginDownloader;
    this.serverPluginJarsInstaller = serverPluginJarsInstaller;
  }

  @Override
  public void define(WebService.NewController controller) {
    controller.createAction("pending")
        .setDescription("Get the list of plugins which will either be installed or removed at the next startup of the SonarQube instance, sorted by archive name")
        .setSince("5.2")
        .setHandler(this)
        .setResponseExample(Resources.getResource(this.getClass(), "example-pending_plugins.json"));
  }

  @Override
  public void handle(Request request, Response response) throws Exception {
    JsonWriter jsonWriter = response.newJsonWriter();

    jsonWriter.beginObject();

    writeInstalling(jsonWriter);

    writeRemoving(jsonWriter);

    jsonWriter.endObject();
    jsonWriter.close();
  }

  private void writeInstalling(JsonWriter jsonWriter) {
    jsonWriter.name(ARRAY_INSTALLING);
    jsonWriter.beginArray();
    for (String fileName : copyOf(CASE_INSENSITIVE_ORDER, pluginDownloader.getDownloadedPluginFilenames())) {
      writeArchive(jsonWriter, fileName);
    }
    jsonWriter.endArray();
  }

  private void writeRemoving(JsonWriter jsonWriter) {
    jsonWriter.name(ARRAY_REMOVING);
    jsonWriter.beginArray();
    for (String fileName : copyOf(CASE_INSENSITIVE_ORDER, serverPluginJarsInstaller.getUninstalledPluginFilenames())) {
      writeArchive(jsonWriter, fileName);
    }
    jsonWriter.endArray();
  }

  private void writeArchive(JsonWriter jsonWriter, String fileName) {
    jsonWriter.beginObject();
    jsonWriter.name(OBJECT_ARTIFACT);
    jsonWriter.beginObject();
    jsonWriter.prop(PROPERTY_NAME, fileName);
    jsonWriter.endObject();
    jsonWriter.endObject();
  }
}
