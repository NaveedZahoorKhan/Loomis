# Loomis

A document scanning Android app inspired by Microsoft Lens. Capture multiple document pages, auto-crop with edge detection, apply filters, combine into a PDF, and share via WhatsApp.

## Features

- **Batch Camera Capture** — Take multiple photos in sequence with CameraX
- **Auto Document Detection** — OpenCV-powered edge detection automatically crops to document boundaries
- **Manual Crop** — Drag corners or move the crop area, with "Auto Detect Edges" button
- **Image Filters** — Auto, Document, Grayscale, Whiteboard enhancement modes
- **Rotate & Reorder** — Rotate pages, delete unwanted ones
- **PDF Generation** — Combines all pages into a single PDF using Android's native PdfDocument API
- **One-Tap WhatsApp Share** — Primary share target, with fallback to system share sheet
- **Additional Export** — Email, Save to Files, Cloud Upload, Print

## Tech Stack

- Kotlin + Jetpack Compose
- CameraX for camera capture
- OpenCV Android SDK for document edge detection
- Android PdfDocument API for PDF generation
- Material 3 with custom Fluent Horizon design system
- Navigation Compose

## Building

Open the `StitchLens` folder in Android Studio and run on a device/emulator.

Requires Android SDK 26+ (Android 8.0).

## License

MIT
