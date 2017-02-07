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
import org.jetbrains.kotlin.descriptors.ModuleDescriptor;
import org.jetbrains.kotlin.js.analyze.TopDownAnalyzerFacadeForJS;
import org.jetbrains.kotlin.js.analyzer.JsAnalysisResult;
import org.jetbrains.kotlin.js.config.JsConfig;
import org.jetbrains.kotlin.js.coroutine.CoroutineTransformer;
import org.jetbrains.kotlin.js.facade.exceptions.TranslationException;
import org.jetbrains.kotlin.js.inline.JsInliner;
import org.jetbrains.kotlin.js.inline.clean.RemoveUnusedImportsKt;
import org.jetbrains.kotlin.js.inline.clean.ResolveTemporaryNamesKt;
import org.jetbrains.kotlin.js.translate.general.AstGenerationResult;
import org.jetbrains.kotlin.js.translate.general.Translation;
import org.jetbrains.kotlin.progress.ProgressIndicatorAndCompilationCanceledStatus;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.resolve.BindingTrace;
import org.jetbrains.kotlin.resolve.diagnostics.Diagnostics;

import java.util.ArrayList;
import java.util.List;

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
        TopDownAnalyzerFacadeForJS.checkForErrors(JsConfig.withJsLibAdded(files, config), bindingTrace.getBindingContext());
        ModuleDescriptor moduleDescriptor = analysisResult.getModuleDescriptor();
        Diagnostics diagnostics = bindingTrace.getBindingContext().getDiagnostics();

        AstGenerationResult translationResult = Translation.generateAst(bindingTrace, files, mainCallParameters, moduleDescriptor, config);
        ProgressIndicatorAndCompilationCanceledStatus.checkCanceled();
        if (hasError(diagnostics)) return new TranslationResult.Fail(diagnostics);

        JsInliner.process(config, analysisResult.getBindingTrace(), translationResult.getInnerModuleName(),
                          translationResult.getFragments(), translationResult.getFragments());

        ResolveTemporaryNamesKt.resolveTemporaryNames(translationResult.getProgram());
        ProgressIndicatorAndCompilationCanceledStatus.checkCanceled();
        if (hasError(diagnostics)) return new TranslationResult.Fail(diagnostics);

        CoroutineTransformer coroutineTransformer = new CoroutineTransformer(translationResult.getProgram());
        coroutineTransformer.accept(translationResult.getProgram());
        RemoveUnusedImportsKt.removeUnusedImports(translationResult.getProgram());
        ProgressIndicatorAndCompilationCanceledStatus.checkCanceled();
        if (hasError(diagnostics)) return new TranslationResult.Fail(diagnostics);

        //expandIsCalls(program, context);
        ProgressIndicatorAndCompilationCanceledStatus.checkCanceled();

        List<String> importedModules = new ArrayList<String>();
        /*for (JsImportedModule module : context.getImportedModules()) {
            importedModules.add(module.getExternalName());
        }*/
        return new TranslationResult.Success(config, files, translationResult.getProgram(), diagnostics, importedModules,
                                             moduleDescriptor, bindingTrace.getBindingContext());
    }
}
