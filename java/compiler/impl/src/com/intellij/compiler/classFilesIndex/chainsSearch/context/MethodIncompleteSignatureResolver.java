/*
 * Copyright 2000-2013 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.compiler.classFilesIndex.chainsSearch.context;

import com.intellij.psi.*;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.containers.FactoryMap;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.jps.classFilesIndex.indexer.impl.MethodIncompleteSignature;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

/**
 * @author Dmitry Batkovich
 */
final class MethodIncompleteSignatureResolver {
  private MethodIncompleteSignatureResolver() {}

  public static FactoryMap<MethodIncompleteSignature, PsiMethod[]> create(final JavaPsiFacade javaPsiFacade, final GlobalSearchScope scope) {
    return new FactoryMap<MethodIncompleteSignature, PsiMethod[]>() {
      @Nullable
      @Override
      protected PsiMethod[] create(final MethodIncompleteSignature signature) {
        return resolveNotDeprecated(signature, javaPsiFacade, scope);
      }
    };
  }

  private static PsiMethod[] resolveNotDeprecated(final MethodIncompleteSignature signature,
                                                 final JavaPsiFacade javaPsiFacade,
                                                 final GlobalSearchScope scope) {
    if (MethodIncompleteSignature.CONSTRUCTOR_METHOD_NAME.equals(signature.getName())) {
      return PsiMethod.EMPTY_ARRAY;
    }
    final PsiClass aClass = javaPsiFacade.findClass(signature.getOwner(), scope);
    if (aClass == null) {
      return PsiMethod.EMPTY_ARRAY;
    }
    final PsiMethod[] methods = aClass.findMethodsByName(signature.getName(), true);
    final List<PsiMethod> filtered = new ArrayList<PsiMethod>(methods.length);
    for (final PsiMethod method : methods) {
      if (method.hasModifierProperty(PsiModifier.STATIC) == signature.isStatic()) {
        final PsiType returnType = method.getReturnType();
        if (returnType != null) {
          if (returnType instanceof PsiClassType) {
            final PsiClass resolved = ((PsiClassType)returnType).resolve();
            if (resolved == null) {
              continue;
            }
            final String qualifiedName = resolved.getQualifiedName();
            if (qualifiedName == null) {
              continue;
            }
            if (qualifiedName.equals(signature.getReturnType())) {
              filtered.add(method);
            }
          } else if (returnType.equalsToText(signature.getReturnType())) {
            filtered.add(method);
          }
        }
      }
    }
    if (filtered.size() > 1) {
      Collections.sort(filtered, new Comparator<PsiMethod>() {
        @Override
        public int compare(final PsiMethod o1, final PsiMethod o2) {
          return o1.getParameterList().getParametersCount() - o2.getParameterList().getParametersCount();
        }
      });
    }
    return filtered.toArray(new PsiMethod[filtered.size()]);
  }
}
