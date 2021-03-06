/*
 * Copyright (C) 2005-2015 Alfresco Software Limited.
 *
 * This file is part of Alfresco
 *
 * Alfresco is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Alfresco is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with Alfresco. If not, see <http://www.gnu.org/licenses/>.
 */
package org.springframework.extensions.surf;

import java.io.IOException;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Map;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.extensions.config.ConfigElement;
import org.springframework.extensions.config.WebFrameworkConfigElement;
import org.springframework.extensions.surf.support.ThreadLocalRequestContext;
import org.springframework.extensions.surf.types.Theme;
import org.springframework.extensions.webscripts.ScriptConfigModel;

import com.asual.lesscss.LessEngine;
import com.asual.lesscss.LessException;

/**
 * Base class for Theme handlers that provide LESS CSS compiling. Provides methods to retrieve the default
 * LESS config from the Surf config and Surf meta objects. Concrete impls can extend this class to provide
 * engine specific implementation but use common LESS config.
 * 
 * @author Kevin Roast
 */
public abstract class LessCssThemeHandler extends CssThemeHandler
{
    private static final Log logger = LogFactory.getLog(LessCssThemeHandler.class);
    
    public static final String LESS_TOKEN = "less-variables";
    
    /**
     * The default LESS configuration. This will be populated with the contents of a file referenced by the 
     * web-framework > defaults > dojo-pages > default-less-configuration.
     */
    private String defaultLessConfig = null;
    
    /**
     * Returns the current default LESS configuration. If it has not previously been retrieved then it will
     * attempt to load it.
     * 
     * @return A String containing the default LESS configuration variables.
     */
    @SuppressWarnings("unchecked")
    protected String getDefaultLessConfig()
    {
        final RequestContext rc = ThreadLocalRequestContext.getRequestContext();
        if (this.defaultLessConfig == null)
        {
            String defaultLessConfigPath = null;
            ScriptConfigModel config = rc.getExtendedScriptConfigModel(null);
            Map<String, ConfigElement> configs = (Map<String, ConfigElement>)config.getScoped().get("WebFramework");
            if (configs != null)
            {
                WebFrameworkConfigElement wfce = (WebFrameworkConfigElement) configs.get("web-framework");
                defaultLessConfigPath = wfce.getDojoDefaultLessConfig();
            }
            else
            {
                defaultLessConfigPath = this.getWebFrameworkConfigElement().getDojoDefaultLessConfig();
            }
            try
            {
                InputStream in = this.getDependencyHandler().getResourceInputStream(defaultLessConfigPath);
                if (in != null)
                {
                    this.defaultLessConfig = this.getDependencyHandler().convertResourceToString(in);
                }
                else
                {
                    if (logger.isErrorEnabled())
                    {
                        logger.error("Could not find the default LESS configuration at: " + defaultLessConfigPath);
                    }
                    // Set the configuration as the empty string as it's not in the configured location
                    this.defaultLessConfig = "";
                }
            }
            catch (IOException e)
            {
                if (logger.isErrorEnabled())
                {
                    logger.error("An exception occurred retrieving the default LESS configuration from: " + defaultLessConfigPath, e);
                }
            }
        }
        return defaultLessConfig;
    }
    
    /**
     * Looks for the LESS CSS token which should contain the LESS style variables that 
     * can be applied to each CSS file. This will be prepended to each CSS file processed.
     * 
     * @return The String of LESS variables.
     */
    public String getLessVariables()
    {
        String variables = this.getDefaultLessConfig();
        Theme currentTheme = ThreadLocalRequestContext.getRequestContext().getTheme();
        if (currentTheme == null)
        {
            currentTheme = ThreadLocalRequestContext.getRequestContext().getObjectService().getTheme("default");
        }
        String themeVariables = currentTheme.getCssTokens().get(LessForJavaCssThemeHandler.LESS_TOKEN);
        if (themeVariables != null)
        {
            // Add a new line just to make sure the first theme specific variable isn't appended to 
            // the end of the last default variable!
            variables += "\n" + themeVariables;
        }
        return variables;
    }
    
    /**
     * This function is used to log exceptions that occur during LESS compilation. Unfortunately the
     * {@link LessException} that is thrown from the {@link LessEngine} does not capture all exception
     * eventualities. When a JavaScript error occurs in Rhino this can result in a {@link ClassCastException}
     * which needs to be caught separately. Currently Surf still supports Java 6 so cannot process
     * multiple exceptions so the error handling has been abstracted to a helper method.
     * 
     * @param e The exception that has been thrown.
     * @param path The path being processed that caused the exception
     * @return The error message generated
     */
    public String logLessException(Exception e, String path)
    {
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        e.printStackTrace(pw);
        String errorMsg = "LESS for Java Engine error compiling: '" + path + "': " + sw.toString();
        if (logger.isErrorEnabled())
        {
            logger.error(errorMsg);
        }
        return errorMsg;
    }
}
