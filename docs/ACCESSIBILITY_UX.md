# Accessibility and UX Requirements

## 1. Standard
Target WCAG 2.1 AA as a design and testing objective. Review applicable GIGW guidance for any government-facing deployment. This project is not certified by this target alone.

## 2. Requirements
- All controls have accessible names.
- Forms use visible labels and clear required-field indicators.
- Validation errors identify the field and explain correction.
- Error summary receives focus when appropriate.
- Keyboard users can reach and operate all interactive controls.
- Focus indicator is visible and not obscured.
- Color is not the only way to convey state.
- Text and controls have sufficient contrast.
- Tables have meaningful headers and captions.
- Dialogs manage focus and support Escape where appropriate.
- Status changes are announced to assistive technology.
- Charts have text summaries or data alternatives.
- Responsive layouts support zoom and narrow viewports.
- Language of page/content is declared.
- Avoid time limits unless necessary; provide warning/extension where appropriate.

## 3. UX states
Every data-driven screen defines:
- loading;
- empty;
- error;
- success;
- unauthorized/forbidden;
- stale data;
- offline/network failure where applicable.

## 4. Form UX
- Save draft where appropriate.
- Show progress for multi-step forms.
- Preserve valid input after validation failure.
- Explain required documents before upload.
- Display upload progress and scan status.
- Confirm irreversible actions.
- Do not use ambiguous status labels.
