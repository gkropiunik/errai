/*
 * Copyright 2011 JBoss, by Red Hat, Inc
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.jboss.errai.marshalling.client.marshallers;

import org.jboss.errai.marshalling.client.api.MarshallingSession;
import org.jboss.errai.marshalling.client.api.annotations.ClientMarshaller;
import org.jboss.errai.marshalling.client.api.annotations.ImplementationAliases;
import org.jboss.errai.marshalling.client.api.annotations.ServerMarshaller;
import org.jboss.errai.marshalling.client.api.json.EJArray;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.List;
import java.util.Stack;
import java.util.Vector;

/**
 * @author Mike Brock <cbrock@redhat.com>
 */
@ClientMarshaller(List.class)
@ServerMarshaller(List.class)
@ImplementationAliases({AbstractList.class, ArrayList.class, Vector.class, Stack.class})
public class ListMarshaller extends AbstractCollectionMarshaller<List> {
  public static final ListMarshaller INSTANCE = new ListMarshaller();

  @Override
  public List[] getEmptyArray() {
    return new ArrayList[0];
  }
  
  @Override
  public List doDemarshall(final EJArray o, final MarshallingSession ctx) {
    return marshallToCollection(new ArrayList<Object>(o.size()), o, ctx);
  }
}
