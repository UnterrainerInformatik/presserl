package info.unterrainer.presserl.admin.ui.editor

import info.unterrainer.presserl.admin.resources.Res
import info.unterrainer.presserl.admin.resources.help_caption
import info.unterrainer.presserl.admin.resources.help_headline
import info.unterrainer.presserl.admin.resources.help_kicker
import info.unterrainer.presserl.admin.resources.help_label_caption
import info.unterrainer.presserl.admin.resources.help_label_headline
import info.unterrainer.presserl.admin.resources.help_label_kicker
import info.unterrainer.presserl.admin.resources.help_label_lead
import info.unterrainer.presserl.admin.resources.help_label_lead_image
import info.unterrainer.presserl.admin.resources.help_label_list
import info.unterrainer.presserl.admin.resources.help_label_paragraph
import info.unterrainer.presserl.admin.resources.help_label_quote
import info.unterrainer.presserl.admin.resources.help_label_section
import info.unterrainer.presserl.admin.resources.help_label_subhead
import info.unterrainer.presserl.admin.resources.help_label_subheadline
import info.unterrainer.presserl.admin.resources.help_image
import info.unterrainer.presserl.admin.resources.help_label_image
import info.unterrainer.presserl.admin.resources.help_lead
import info.unterrainer.presserl.admin.resources.help_lead_image
import info.unterrainer.presserl.admin.resources.help_list
import info.unterrainer.presserl.admin.resources.help_paragraph
import info.unterrainer.presserl.admin.resources.help_quote
import info.unterrainer.presserl.admin.resources.help_section
import info.unterrainer.presserl.admin.resources.help_subhead
import info.unterrainer.presserl.admin.resources.help_subheadline
import info.unterrainer.presserl.admin.resources.sample_caption
import info.unterrainer.presserl.admin.resources.sample_headline
import info.unterrainer.presserl.admin.resources.sample_image
import info.unterrainer.presserl.admin.resources.sample_kicker
import info.unterrainer.presserl.admin.resources.sample_lead
import info.unterrainer.presserl.admin.resources.sample_lead_image
import info.unterrainer.presserl.admin.resources.sample_list
import info.unterrainer.presserl.admin.resources.sample_paragraph
import info.unterrainer.presserl.admin.resources.sample_quote
import info.unterrainer.presserl.admin.resources.sample_section
import info.unterrainer.presserl.admin.resources.sample_subhead
import info.unterrainer.presserl.admin.resources.sample_subheadline
import org.jetbrains.compose.resources.StringResource

/** The editor parts that have a field explanation, in the order the reader shows them. */
enum class HelpPart { SECTION, KICKER, HEADLINE, SUBHEADLINE, LEAD_IMAGE, CAPTION, LEAD, PARAGRAPH, SUBHEAD, QUOTE, LIST, IMAGE }

/** Whether the part's explanation carries the image-rights note: the parts that add a photo. */
val HelpPart.showsImageRights: Boolean
    get() = this == HelpPart.LEAD_IMAGE || this == HelpPart.IMAGE

val HeaderField.helpPart: HelpPart
    get() = when (this) {
        HeaderField.KICKER -> HelpPart.KICKER
        HeaderField.HEADLINE -> HelpPart.HEADLINE
        HeaderField.SUBHEADLINE -> HelpPart.SUBHEADLINE
        HeaderField.LEAD -> HelpPart.LEAD
    }

val BlockType.helpPart: HelpPart
    get() = when (this) {
        BlockType.PARAGRAPH -> HelpPart.PARAGRAPH
        BlockType.SUBHEAD -> HelpPart.SUBHEAD
        BlockType.QUOTE -> HelpPart.QUOTE
        BlockType.LIST -> HelpPart.LIST
        BlockType.IMAGE -> HelpPart.IMAGE
    }

/** One or two sentences for a reader of about ten years saying what the part is for. */
val HelpPart.explanation: StringResource
    get() = when (this) {
        HelpPart.SECTION -> Res.string.help_section
        HelpPart.KICKER -> Res.string.help_kicker
        HelpPart.HEADLINE -> Res.string.help_headline
        HelpPart.SUBHEADLINE -> Res.string.help_subheadline
        HelpPart.LEAD_IMAGE -> Res.string.help_lead_image
        HelpPart.CAPTION -> Res.string.help_caption
        HelpPart.LEAD -> Res.string.help_lead
        HelpPart.PARAGRAPH -> Res.string.help_paragraph
        HelpPart.SUBHEAD -> Res.string.help_subhead
        HelpPart.QUOTE -> Res.string.help_quote
        HelpPart.LIST -> Res.string.help_list
        HelpPart.IMAGE -> Res.string.help_image
    }

/** The question-mark button's accessible label, naming the part ("What is the kicker?"). */
val HelpPart.label: StringResource
    get() = when (this) {
        HelpPart.SECTION -> Res.string.help_label_section
        HelpPart.KICKER -> Res.string.help_label_kicker
        HelpPart.HEADLINE -> Res.string.help_label_headline
        HelpPart.SUBHEADLINE -> Res.string.help_label_subheadline
        HelpPart.LEAD_IMAGE -> Res.string.help_label_lead_image
        HelpPart.CAPTION -> Res.string.help_label_caption
        HelpPart.LEAD -> Res.string.help_label_lead
        HelpPart.PARAGRAPH -> Res.string.help_label_paragraph
        HelpPart.SUBHEAD -> Res.string.help_label_subhead
        HelpPart.QUOTE -> Res.string.help_label_quote
        HelpPart.LIST -> Res.string.help_label_list
        HelpPart.IMAGE -> Res.string.help_label_image
    }

/**
 * The part's text in the sample article; the list's items are separated by " · ", the image placeholders name the
 * photo (the body image's caption is [Res.string.sample_image_caption]).
 */
val HelpPart.sample: StringResource
    get() = when (this) {
        HelpPart.SECTION -> Res.string.sample_section
        HelpPart.KICKER -> Res.string.sample_kicker
        HelpPart.HEADLINE -> Res.string.sample_headline
        HelpPart.SUBHEADLINE -> Res.string.sample_subheadline
        HelpPart.LEAD_IMAGE -> Res.string.sample_lead_image
        HelpPart.CAPTION -> Res.string.sample_caption
        HelpPart.LEAD -> Res.string.sample_lead
        HelpPart.PARAGRAPH -> Res.string.sample_paragraph
        HelpPart.SUBHEAD -> Res.string.sample_subhead
        HelpPart.QUOTE -> Res.string.sample_quote
        HelpPart.LIST -> Res.string.sample_list
        HelpPart.IMAGE -> Res.string.sample_image
    }
