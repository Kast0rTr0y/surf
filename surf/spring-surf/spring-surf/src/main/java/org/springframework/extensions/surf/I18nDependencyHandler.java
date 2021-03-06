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
import java.io.Writer;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Map.Entry;
import java.util.PropertyResourceBundle;
import java.util.ResourceBundle;
import java.util.Set;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import org.springframework.extensions.config.WebFrameworkConfigElement;
import org.springframework.extensions.surf.DojoDependencies.I18nDependency;
import org.springframework.extensions.surf.util.CacheReport;
import org.springframework.extensions.surf.util.CacheReporter;
import org.springframework.extensions.surf.util.I18NUtil;
import org.springframework.extensions.surf.util.StringBuilderWriter;
import org.springframework.extensions.webscripts.WebScriptException;
import org.springframework.extensions.webscripts.json.JSONWriter;

public class I18nDependencyHandler implements CacheReporter
{
    /**
     * A {@link DependencyHandler} is required for retrieving the properties file contents.
     */
    private DependencyHandler dependencyHandler;

    private WebFrameworkConfigElement webFrameworkConfigElement;
    
    /**
     * This method generates a JavaScript string from the {@link Map} of supplied {@link DojoDependencies}. The String generated
     * will be able to add all the message properties referenced from the dependencies into global message objects that can then
     * be accessed by widgets.
     * 
     * @param dependencyMap Map<String, DojoDependencies>
     * @return String
     */
    public String generateI18nJavaScript(Map<String, DojoDependencies> dependencyMap)
    {
        StringBuilder content = new StringBuilder();
        
        Map<String, Map<String, Object>> m = generateScopeToBundleMap(dependencyMap);
        String globalMessagesObject = this.webFrameworkConfigElement.getDojoMessagesObject();
        
        // Check that the global messages object has been defined and create if it hasn't...
        content.append("if (typeof ");
        content.append(globalMessagesObject);
        content.append(" === 'undefined') {\n   ");
        content.append(globalMessagesObject);
        content.append(" = {};\n}\nif(");
        content.append(globalMessagesObject);
        content.append(".messages == null) {\n   ");
        content.append(globalMessagesObject);
        content.append(".messages = {};\n}\nif(");
        content.append(globalMessagesObject);
        content.append(".messages.global == null) {\n   ");
        content.append(globalMessagesObject);
        content.append(".messages.global = ");
        Writer writer = new StringBuilderWriter(8192);
        JSONWriter out = new JSONWriter(writer);
        try
        {
            out.startObject();
            Map<String, String> messages = I18NUtil.getAllMessages(I18NUtil.parseLocale(I18NUtil.getLocale().toString()));
            for (Map.Entry<String, String> entry : messages.entrySet())
            {
                out.writeValue(entry.getKey(), entry.getValue());
            }
            out.endObject();
            writer.write(";\r\n");
        }
        catch (IOException jsonErr)
        {
            throw new WebScriptException("Error building messages response.", jsonErr);
        }
        content.append(writer.toString());
        content.append("\n}\n");
        
        content.append(globalMessagesObject);
        content.append(".messages.defaultScope = '");
        content.append(this.webFrameworkConfigElement.getDojoMessagesDefaultScope());
        content.append("';\n");
        
        // Create the scope map...
        content.append("if(");
        content.append(globalMessagesObject);
        content.append(".messages.scope == null) {\n   ");
        content.append(globalMessagesObject);
        content.append(".messages.scope = {};\n}\n");
        
        // Add all the scoped messages...
        for (Entry<String, Map<String, Object>> e: m.entrySet())
        {
            if (e.getValue() != null)
            {
                // Set the default messages scope...
                String msgs = JSONWriter.encodeToJSON(e.getValue());
                content.append("var cScope = ");
                content.append(msgs);
                content.append(";\n   if (");
                content.append(globalMessagesObject);
                content.append(".messages.scope['");
                content.append(e.getKey());
                content.append("']) {\n   for (var key in cScope) { \n      ");
                content.append(globalMessagesObject);
                content.append(".messages.scope['");
                content.append(e.getKey());
                content.append("'][key] = cScope[key];\n}\n}\nelse {\n   ");
                content.append(globalMessagesObject);
                content.append(".messages.scope['");
                content.append(e.getKey());
                content.append("'] = ");
                content.append(msgs);
                content.append(";\n}\n");
            }
        }
        return content.toString();
    }
    
    /**
     * This method works through the supplied map of {@link DojoDependencies} and generated a map of each requested
     * scope to a map of properties generated from all {@link ResourceBundle} instances that have been assigned to
     * that scope.
     * 
     * @param dependencyMap Map<String, DojoDependencies>
     * @return Map
     */
    public Map<String, Map<String, Object>> generateScopeToBundleMap(Map<String, DojoDependencies> dependencyMap)
    {
        Map<String, Map<String, Object>> scopeToMapMap = new HashMap<String, Map<String,Object>>();
        
        // Work through the supplied set of dependencies and create a map for each scope...
        for (Entry<String, DojoDependencies> deps: dependencyMap.entrySet())
        {
            DojoDependencies currDeps = deps.getValue();
            if (currDeps != null)
            {
                Set<I18nDependency> i18nDeps = currDeps.getI18nDeps();
                if (i18nDeps != null)
                {
                    for (I18nDependency i18nDep: deps.getValue().getI18nDeps())
                    {
                        // The the map of properties generated from all the available ResourceBundles associated
                        // with the current path...
                        Map<String, Object> bundleMap = getLocaleMergedBundle(i18nDep.getPath());
                        if (bundleMap != null)
                        {
                            String scope = i18nDep.getScope();
                            Map<String, Object> mergedBundlesForScope = scopeToMapMap.get(scope);
                            if (mergedBundlesForScope == null)
                            {
                                mergedBundlesForScope = new HashMap<String, Object>();
                                scopeToMapMap.put(scope, mergedBundlesForScope);
                            }
                            
                            // Merged all the properties into the scoped merged map...
                            mergedBundlesForScope.putAll(bundleMap);
                        }
                    }
                }
            }
        }
        return scopeToMapMap;
    }
    
    /**
     * This method generates a {@link Map} of NLS properties comprised of all the available {@link ResourceBundle}
     * instances retrieved from the supplied path. The path supplied is assumed to be that of the default {@link Locale}
     * and the current {@link Locale} is then used to derive paths specific to language, country and variant. A {@link ResourceBundle}
     * is generated from each path and the contents of each {@link ResourceBundle} is merged into the {@link Map} that is returned.
     *  
     * @param path String
     * @return Map
     */
    public Map<String, Object> getLocaleMergedBundle(String path)
    {
        final Locale locale = I18NUtil.getLocale();
        Map<String, Object> mergedBundles = getCachedBundle(path, locale);
        if (mergedBundles == null)
        {
            // Work out all possible bundle paths...
            String languagePath = null;
            String countryPath = null;
            String variantPath = null;
            if (path != null)
            {
                int lastFullStop = path.lastIndexOf(CssImageDataHandler.FULL_STOP);
                if (lastFullStop != -1)
                {
                    String prefix = path.substring(0, lastFullStop);
                    String suffix = path.substring(lastFullStop);
                    if (!locale.getLanguage().equals(""))
                    {
                        languagePath = prefix + "_" + locale.getLanguage() + suffix;
                        if (!locale.getCountry().equals(""))
                        {
                            countryPath = prefix + "_" + locale.getLanguage() + "_" + locale.getCountry() + suffix;
                            if (!locale.getVariant().equals(""))
                            {
                                variantPath = prefix + "_" + locale.getLanguage() + "_" + locale.getCountry() + "_" + locale.getVariant() + suffix;
                            }
                        }
                    }
                }
            }
            
            // Get the available bundles...
            ResourceBundle defaultBundle = getBundle(path);
            ResourceBundle languageBundle = getBundle(languagePath);
            ResourceBundle countryBundle = getBundle(countryPath);
            ResourceBundle variantBundle = getBundle(variantPath);

            // Merge the bundles so that the most specific bundle "wins"...
            mergedBundles = new HashMap<String, Object>();
            mergeBundle(mergedBundles, defaultBundle);
            mergeBundle(mergedBundles, languageBundle);
            mergeBundle(mergedBundles, countryBundle);
            mergeBundle(mergedBundles, variantBundle);

            // Cache the result for future access...
            cacheBundle(path, locale, mergedBundles);
        }
        return mergedBundles;
    }
    
    /**
     * 
     * @param path String
     * @return ResourceBundle
     */
    protected ResourceBundle getBundle(String path)
    {
        ResourceBundle b = null;
        try
        {
            if (path != null)
            {
                InputStream in = this.dependencyHandler.getResourceInputStream(path);
                if (in != null)
                {
                    b = new PropertyResourceBundle(in);
                }
            }
        }
        catch (IOException e)
        {
            // No action required. If an error occurs getting the bundle we'll just return null.
        }
        return b;
    }
    
    /**
     * 
     * @param mergedBundles Map<String, Object>
     * @param bundleToMerge ResourceBundle
     */
    protected void mergeBundle(Map<String, Object> mergedBundles, ResourceBundle bundleToMerge)
    {
        if (mergedBundles != null && bundleToMerge != null)
        {
            Enumeration<String> keys = bundleToMerge.getKeys();
            while (keys.hasMoreElements())
            {
                String key = keys.nextElement();
                mergedBundles.put(key, bundleToMerge.getObject(key));
            }
        }
    }
    
    /**
     * Lock for protecting access to the bundle cache.
     */
    private ReentrantReadWriteLock bundleCacheLock = new ReentrantReadWriteLock();
    
    /**
     * Cache of path to map of locale to JSON string
     */
    private Map<String, Map<Locale, Map<String, Object>>> bundleCache = new HashMap<String, Map<Locale, Map<String, Object>>>();
    
    /**
     * Checks the cache for previously generated JSON strings.
     * @param path The path to check the cache for
     * @param locale The locale required.
     * @return Map
     */
    public Map<String, Object> getCachedBundle(String path, Locale locale)
    {
        Map<String, Object> b = null;
        this.bundleCacheLock.readLock().lock();
        try
        {
            Map<Locale, Map<String, Object>> m = this.bundleCache.get(path);
            if (m != null)
            {
                b = m.get(locale);
            }
        }
        finally
        {
            this.bundleCacheLock.readLock().unlock();
        }
        return b;
    }
    
    /**
     * Add a new generated JSON string for a specific locale for a specific path
     * @param path String
     * @param locale Locale
     * @param s Map<String, Object>
     */
    public void cacheBundle(String path, Locale locale, Map<String, Object> s)
    {
        this.bundleCacheLock.writeLock().lock();
        try
        {
            Map<Locale, Map<String, Object>> m = this.bundleCache.get(path);
            if (m == null)
            {
                m = new HashMap<Locale, Map<String, Object>>();
                this.bundleCache.put(path, m);
            }
            m.put(locale,s);
        }
        finally
        {
            this.bundleCacheLock.writeLock().unlock();
        }
    }
    
    /**
     * Clears the cache.
     */
    @Override
    public void clearCaches()
    {
        this.bundleCacheLock.writeLock().lock();
        try
        {
           this.bundleCache.clear();
        }
        finally
        {
            this.bundleCacheLock.writeLock().unlock();
        }
    }
    
    @Override
    public List<CacheReport> report()
    {
        List<CacheReport> reports = new ArrayList<>(3);
        
        long size = 0;
        this.bundleCacheLock.writeLock().lock();
        try
        {
            for (Map<Locale, Map<String, Object>> m : this.bundleCache.values())
            {
                for (Map<String, Object> b : m.values())
                {
                    for (Object o : b.values())
                    {
                        size += ((String)o).length()*2 + 64;
                    }
                }
            }
            reports.add(new CacheReport("bundleCache", this.bundleCache.size(), size));
        }
        finally
        {
            this.bundleCacheLock.writeLock().unlock();
        }
        
        return reports;
    }

    public DependencyHandler getDependencyHandler()
    {
        return dependencyHandler;
    }

    public void setDependencyHandler(DependencyHandler dependencyHandler)
    {
        this.dependencyHandler = dependencyHandler;
    }

    public WebFrameworkConfigElement getWebFrameworkConfigElement()
    {
        return webFrameworkConfigElement;
    }

    public void setWebFrameworkConfigElement(WebFrameworkConfigElement webFrameworkConfigElement)
    {
        this.webFrameworkConfigElement = webFrameworkConfigElement;
    }
}
