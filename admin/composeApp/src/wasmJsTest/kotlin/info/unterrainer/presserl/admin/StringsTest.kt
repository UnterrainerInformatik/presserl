@file:OptIn(ExperimentalWasmJsInterop::class)

package info.unterrainer.presserl.admin

import kotlin.js.ExperimentalWasmJsInterop
import kotlin.js.JsAny
import kotlin.js.Promise
import kotlin.js.js
import kotlin.test.Test

/** Rejects unless both files define the same string names (and at least one). */
private fun verifySameKeys(): Promise<JsAny?> = js(
    """Promise.all(['values', 'values-en'].map(qualifier =>
    fetch('/strings/' + qualifier + '/strings.xml').then(response => response.text()).then(text => {
        const names = [...new DOMParser().parseFromString(text, 'application/xml').querySelectorAll('string')]
            .map(element => element.getAttribute('name'));
        if (names.length === 0) throw new Error(qualifier + '/strings.xml defines no strings');
        return new Set(names);
    }))
).then(([german, english]) => {
    const onlyGerman = [...german].filter(name => !english.has(name));
    const onlyEnglish = [...english].filter(name => !german.has(name));
    if (onlyGerman.length > 0 || onlyEnglish.length > 0) {
        throw new Error('Only in values: ' + onlyGerman.join(', ') + '; only in values-en: ' + onlyEnglish.join(', '));
    }
    return null;
})""",
)

/** Rejects unless both files define the section strings of the editor with the expected texts. */
private fun verifySectionStrings(): Promise<JsAny?> = js(
    """Promise.all([['values', 'Ressort'], ['values-en', 'Section']].map(([qualifier, expected]) =>
    fetch('/strings/' + qualifier + '/strings.xml').then(response => response.text()).then(text => {
        const strings = new DOMParser().parseFromString(text, 'application/xml');
        const value = name => strings.querySelector('string[name="' + name + '"]')?.textContent;
        if (value('field_section') !== expected) throw new Error(qualifier + ': field_section is ' + value('field_section'));
        if (!value('choose_section')) throw new Error(qualifier + ': choose_section is missing');
    }))
).then(() => null)""",
)

/** Rejects unless both files define the issue screen labels with the expected texts. */
private fun verifyIssueStrings(): Promise<JsAny?> = js(
    """Promise.all([
    ['values', { nav_issues: 'Ausgaben', new_issue: 'Neue Ausgabe', field_publication_date: 'Erscheinungsdatum' }],
    ['values-en', { nav_issues: 'Issues', new_issue: 'New issue', field_publication_date: 'Publication date' }],
].map(([qualifier, expected]) =>
    fetch('/strings/' + qualifier + '/strings.xml').then(response => response.text()).then(text => {
        const strings = new DOMParser().parseFromString(text, 'application/xml');
        Object.entries(expected).forEach(([name, value]) => {
            const actual = strings.querySelector('string[name="' + name + '"]')?.textContent;
            if (actual !== value) throw new Error(qualifier + ': ' + name + ' is ' + actual);
        });
    }))
).then(() => null)""",
)

/** Rejects unless both files define the image block's strings with the expected texts. */
private fun verifyImageBlockStrings(): Promise<JsAny?> = js(
    """Promise.all([
    ['values', { block_image: 'Bild', help_label_image: 'Was ist ein Bild im Text?' }],
    ['values-en', { block_image: 'Image', help_label_image: 'What is an image in the text?' }],
].map(([qualifier, expected]) =>
    fetch('/strings/' + qualifier + '/strings.xml').then(response => response.text()).then(text => {
        const strings = new DOMParser().parseFromString(text, 'application/xml');
        Object.entries(expected).forEach(([name, value]) => {
            const actual = strings.querySelector('string[name="' + name + '"]')?.textContent;
            if (actual !== value) throw new Error(qualifier + ': ' + name + ' is ' + actual);
        });
        ['help_image', 'sample_image', 'sample_image_caption'].forEach(name => {
            if (!strings.querySelector('string[name="' + name + '"]')?.textContent) throw new Error(qualifier + ': ' + name + ' is missing');
        });
    }))
).then(() => null)""",
)

/** German (default) and English must stay complete; a missing key would silently fall back to German. */
class StringsTest {

    @Test
    fun germanAndEnglishDefineTheSameKeys(): Promise<JsAny?> = verifySameKeys()

    @Test
    fun sectionIsRessortInGermanAndSectionInEnglish(): Promise<JsAny?> = verifySectionStrings()

    @Test
    fun issueLabelsInGermanAndEnglish(): Promise<JsAny?> = verifyIssueStrings()

    @Test
    fun imageBlockLabelsInGermanAndEnglish(): Promise<JsAny?> = verifyImageBlockStrings()
}
