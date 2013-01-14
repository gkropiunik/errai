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

package org.jboss.errai.databinding.client;

/**
 * Thrown to indicate that a widget could not be bound to a model property because it is already bound to a property of
 * the same model instance.
 * 
 * @author Christian Sadilek <csadilek@redhat.com>
 */
@SuppressWarnings("serial")
public class WidgetAlreadyBoundException extends RuntimeException {

  public WidgetAlreadyBoundException(String message) {
    super(message);
  }

}
