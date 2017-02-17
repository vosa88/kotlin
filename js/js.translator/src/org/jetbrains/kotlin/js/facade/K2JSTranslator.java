/*
 * Copyright 2010-2015 JetBrains s.r.o.
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

package org.jetbrains.kotlin.js.facade;

import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.kotlin.descriptors.DeclarationDescriptor;
import org.jetbrains.kotlin.descriptors.ModuleDescriptor;
import org.jetbrains.kotlin.js.analyze.TopDownAnalyzerFacadeForJS;
import org.jetbrains.kotlin.js.analyzer.JsAnalysisResult;
import org.jetbrains.kotlin.js.backend.ast.JsImportedModule;
import org.jetbrains.kotlin.js.backend.ast.JsProgramFragment;
import org.jetbrains.kotlin.js.config.JSConfigurationKeys;
import org.jetbrains.kotlin.js.config.JsConfig;
import org.jetbrains.kotlin.js.coroutine.CoroutineTransformer;
import org.jetbrains.kotlin.js.facade.exceptions.TranslationException;
import org.jetbrains.kotlin.js.inline.JsInliner;
import org.jetbrains.kotlin.js.inline.clean.RemoveUnusedImportsKt;
import org.jetbrains.kotlin.js.inline.clean.ResolveTemporaryNamesKt;
import org.jetbrains.kotlin.js.translate.general.AstGenerationResult;
import org.jetbrains.kotlin.js.translate.general.FileTranslationResult;
import org.jetbrains.kotlin.js.translate.general.Translation;
import org.jetbrains.kotlin.js.translate.utils.ExpandIsCallsKt;
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.resolve.BindingTrace;
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics;
import org.jetbrains.kotlin.serialization.js.ast.JsAstDeserializer;
import org.jetbrains.kotlin.serialization.js.ast.JsAstSerializer;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.jetbrains.kotlin.diagnostics.DiagnosticUtils.hasError;

/**
 * An entry point of translator.
 */
public final class K2JSTranslator {

    @NotNull
    private final JsConfig config;

    public K2JSTranslator(@NotNull JsConfig config) {
        this.config = config;
    }

    @NotNull
    public TranslationResult translate(
            @NotNull List<KtFile> files,
            @NotNull MainCallParameters mainCallParameters
    ) throws TranslationException {
        return translate(files, mainCallParameters, null);
    }

    @NotNull
    public TranslationResult translate(
            @NotNull List<KtFile> files,
            @NotNull MainCallParameters mainCallParameters,
            @Nullable JsAnalysisResult analysisResult
    ) throws TranslationException {
        if (analysisResult == null) {
            analysisResult = TopDownAnalyzerFacadeForJS.analyzeFiles(files, config);
            ProgressIndicatorAndCompilationCanceledStatus.checkCanceled();
        }

        BindingTrace bindingTrace = analysisResult.getBindingTrace();
        TopDownAnalyzerFacadeForJS.checkForErrors(files, bindingTrace.getBindingContext());
        ModuleDescriptor moduleDescriptor = analysisResult.getModuleDescriptor();
        Diagnostics diagnostics = bindingTrace.getBindingContext().getDiagnostics();

        AstGenerationResult translationResult = Translation.generateAst(bindingTrace, files, mainCallParameters, moduleDescriptor, config);
        ProgressIndicatorAndCompilationCanceledStatus.checkCanceled();
        if (hasError(diagnostics)) return new TranslationResult.Fail(diagnostics);

        List<JsProgramFragment> newFragments = translationResult.getFragments();
        List<JsProgramFragment> allFragments = new ArrayList<JsProgramFragment>(newFragments);
        List<byte[]> deserializedFragmentsData = config.getConfiguration().get(JSConfigurationKeys.DESERIALIZED_FRAGMENTS);
        if (deserializedFragmentsData != null) {
            JsAstDeserializer deserializer = new JsAstDeserializer(translationResult.getProgram());
            for (byte[] fragmentData : deserializedFragmentsData) {
                allFragments.add(deserializer.deserialize(new ByteArrayInputStream(fragmentData)));
            }
        }

        JsInliner.process(config, analysisResult.getBindingTrace(), translationResult.getInnerModuleName(), allFragments, newFragments);

        Map<KtFile, FileTranslationResult> fileMap = new HashMap<KtFile, FileTranslationResult>();
        JsAstSerializer serializer = new JsAstSerializer();
        boolean serializeFragments = config.getConfiguration().get(JSConfigurationKeys.SERIALIZE_FRAGMENTS, false);
        for (KtFile file : files) {
            List<DeclarationDescriptor> scope = translationResult.getFileMemberScopes().get(file);
            byte[] binaryAst = null;
            if (serializeFragments) {
                JsProgramFragment fragment = translationResult.getFragmentMap().get(file);
                if (fragment != null) {
                    ByteArrayOutputStream output = new ByteArrayOutputStream();
                    serializer.serialize(fragment, output);
                    binaryAst = output.toByteArray();
                }
            }
            fileMap.put(file, new FileTranslationResult(file, scope, binaryAst));
        }

        ResolveTemporaryNamesKt.resolveTemporaryNames(translationResult.getProgram());
        ProgressIndicatorAndCompilationCanceledStatus.checkCanceled();
        if (hasError(diagnostics)) return new TranslationResult.Fail(diagnostics);

        CoroutineTransformer coroutineTransformer = new CoroutineTransformer(translationResult.getProgram());
        coroutineTransformer.accept(translationResult.getProgram());
        RemoveUnusedImportsKt.removeUnusedImports(translationResult.getProgram());
        ProgressIndicatorAndCompilationCanceledStatus.checkCanceled();
        if (hasError(diagnostics)) return new TranslationResult.Fail(diagnostics);

        ExpandIsCallsKt.expandIsCalls(newFragments);
        ProgressIndicatorAndCompilationCanceledStatus.checkCanceled();

        List<String> importedModules = new ArrayList<String>();
        for (JsImportedModule module : translationResult.getImportedModuleList()) {
            importedModules.add(module.getExternalName());
        }
        return new TranslationResult.Success(config, files, translationResult.getProgram(), diagnostics, importedModules,
                                             moduleDescriptor, bindingTrace.getBindingContext(), fileMap);
    }
}
