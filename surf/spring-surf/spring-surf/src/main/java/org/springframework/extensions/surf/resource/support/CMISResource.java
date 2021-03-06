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

package org.springframework.extensions.surf.resource.support;

import java.io.IOException;
import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.dom4j.Element;
import org.dom4j.Namespace;
import org.dom4j.QName;
import org.springframework.extensions.surf.FrameworkBean;
import org.springframework.extensions.surf.resource.AbstractResource;
import org.springframework.extensions.surf.resource.ResourceContent;
import org.springframework.extensions.surf.resource.ResourceContentImpl;
import org.springframework.extensions.surf.resource.ResourceXMLContent;
import org.springframework.extensions.surf.resource.ResourceXMLContentImpl;
import org.springframework.extensions.surf.util.XMLUtil;

/**
 * CMIS resource
 * 
 * Content = heavy asset
 * Metadata = CMIS information
 * 
 * Object ids are of the following format:
 * 
 *    cmis://<endpointId>/node/workspace/SpacesStore/790ccbc3-a3ee-45ba-b169-0926ad77c2c8
 *    cmis://<endpointId>/cmis
 *    cmis://<endpointId>/types
 *    cmis://<endpointId>/queries
 *     
 * @author muzquiano
 */
public class CMISResource extends AbstractResource
{
    private static Log logger = LogFactory.getLog(CMISResource.class);
    
    // some useful CMIS namespaces
    public static Namespace NAMESPACE_APP = Namespace.get("app", "http://www.w3.org/2007/app");
    public static Namespace NAMESPACE_CMISRA = Namespace.get("cmisra", "http://docs.oasis-open.org/ns/cmis/restatom/200901");
    public static Namespace NAMESPACE_CMIS = Namespace.get("cmis", "http://docs.oasis-open.org/ns/cmis/core/200901");
    public static Namespace NAMESPACE_ALF = Namespace.get("alf", "http://www.alfresco.org");
    public static Namespace NAMESPACE_OPENSEARCH = Namespace.get("opensearch", "http://a9.com/-/spec/opensearch/1.1/");
    
    private String objectTypeId = null;
    protected FrameworkBean frameworkUtil;
    
    public CMISResource(String protocolId, String endpointId, String objectId, FrameworkBean frameworkUtil)
    {
        super(protocolId, endpointId, objectId, frameworkUtil);
    }
    
    /* (non-Javadoc)
     * @see org.alfresco.web.framework.resource.Resource#getMetadata()
     */
    public ResourceContent getMetadata() throws IOException
    {
        return new ResourceXMLContentImpl(this, getMetadataURL(), frameworkUtil);
    }
    
    /* (non-Javadoc)
     * @see org.alfresco.web.framework.resource.Resource#getMetadataURL()
     */
    public String getMetadataURL()
    {
        String metadataURL = "/api";
        if (this.getObjectId() != null)
        {
            metadataURL = "/api/" + this.getObjectId();
        }

        if (logger.isDebugEnabled())
            logger.debug("CMIS resource metadata url: " + metadataURL);
        
        return metadataURL;        
    }

    /* (non-Javadoc)
     * @see org.alfresco.web.framework.resource.Resource#getContent()
     */
    public ResourceContent getContent() throws IOException
    {
        ResourceContent content = null;
        
        if (getContentURL() != null)
        {
            content = new ResourceContentImpl(this, getContentURL(), frameworkUtil);
        }
        
        return content;
    }

    /* (non-Javadoc)
     * @see org.alfresco.web.framework.resource.Resource#getContentURL()
     */
    public String getContentURL()
    {
        String contentURL = null;
        
        if (this.getObjectId() != null)
        {
            contentURL =  "/api/" + this.getObjectId() + "/content";
        }
        
        if (logger.isDebugEnabled())
            logger.debug("CMIS resource content url: " + contentURL);
        
        return contentURL;
    }
    
    /* (non-Javadoc)
     * @see org.alfresco.web.framework.resource.Resource#getObjectTypeId()
     */
    public synchronized String getObjectTypeId()
    {
        if (objectTypeId == null)
        {
            try
            {
                ResourceXMLContent rc = (ResourceXMLContent) this.getMetadata();
                Element rootElement = rc.getDocument().getRootElement();
                
                if ("entry".equals(rootElement.getName()))
                {
                    Element cmisraObject = rootElement.element(QName.get("object", NAMESPACE_CMISRA));
                    Element cmisProperties = cmisraObject.element(QName.get("properties", NAMESPACE_CMIS));
                    List propertyIds = cmisProperties.elements(QName.get("propertyId", NAMESPACE_CMIS));
                    for (int i = 0; i < propertyIds.size(); i++)
                    {
                        Element propertyId = (Element) propertyIds.get(i);
                        
                        if ("cmis:baseTypeId".equals(propertyId.attribute("propertyDefinitionId").getValue()))
                        {
                            Element valueElement = propertyId.element(QName.get("value", NAMESPACE_CMIS));
                            objectTypeId = XMLUtil.getValue(valueElement);
                        }
                    }
                }
            }
            catch (Exception e)
            {
                e.printStackTrace();
            }
            
        }
        
        return objectTypeId;
    }
    
    /* (non-Javadoc)
     * @see org.alfresco.web.framework.resource.AbstractResource#isContainer()
     */
    public boolean isContainer()
    {
        // TODO: determine whether the cmis resource is a container...?
        return false;
    }    
}
