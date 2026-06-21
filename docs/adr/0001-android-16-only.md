# Target Android 16 Only

Kartoffel targets Android 16 and does not need to support earlier Android versions for the MVP because the initial user is also the primary user. This lets the app use current Android platform behavior and APIs directly where they are materially better, while accepting that lowering the minimum version later may require revisiting implementation choices.
