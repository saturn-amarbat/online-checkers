# Checkers Pro UI Guardrails And Audit Playbook

## Immediate Next Action Template

Use this exact instruction for future UI passes:

Generate the JavaFX CSS file (style.css) to match the Figma wireframes and show how to link it in GuiClient.java.

## CSS Linking Pattern In GuiClient.java

Use explicit scene linking in Java code:

```java
private static final String APP_CSS = "/styles/checkers-pro.css";

private Scene createStyledScene(Region root) {
    Scene scene = new Scene(root, 1024, 600);
    scene.getStylesheets().add(getClass().getResource(APP_CSS).toExternalForm());
    return scene;
}
```

If you switch to style.css, change APP_CSS to:

```java
private static final String APP_CSS = "/styles/style.css";
```

## Pitfalls To Watch

1. JavaFX CSS syntax must use -fx- properties.
2. Do not add unapproved dependencies (no Netty, no Gson, no extra runtime libs unless explicitly approved).
3. All network-driven UI updates must run on JavaFX thread with Platform.runLater().
4. Keep logic explainable for audit: explicit for-loops and if/else blocks.
5. Avoid complex streams, reflection, and dense ternary chains.

## Architecture Rules (Course Compliance)

1. Networking uses java.net.Socket and Serializable Message objects.
2. UI event handlers use lambda callbacks and send Message objects through clientConnection.send(message).
3. Keep standard Maven workflow compatible with mvn exec:java.

## Figma Interpretation Policy

1. Preserve screen intent and hierarchy over exact pixel matching.
2. Improve spacing and alignment where wireframes are rough.
3. Keep 1024x600 as base reference and allow responsive scaling.
4. Prefer clean visual rhythm: consistent card padding, heading sizes, and side-panel widths.

## Review Protocol

1. Implement or refactor UI.
2. Verify local build and run.
3. Commit and push.
4. Send GuiClient.java and CSS diff/files for final compliance audit and UML prep.

## Current Project Notes

1. Current stylesheet path: /styles/style.css (imports checkers-pro.css)
2. Current class has Platform.runLater wrapping for network callbacks.
3. Keep this playbook updated before major UI refactors.

## Today’s Progress Summary

1. Created and pushed `hw5-pure-submission` as the simplified HW5 release branch.
2. Removed Project 3-only engine, database, and CSS artifacts from that branch.
3. Packaged a cleaner HW5 submission zip with build outputs excluded.
4. Returned the workspace to `main` so Project 3 work can continue separately.

## How To Run HW5

1. Open a terminal in `/home/saturn/hw5.startercode`.
2. Run `cd HW5Server && mvn clean compile`.
3. Run `cd ../HW5Client && mvn clean compile`.
4. To launch the server, run `cd HW5Server && mvn exec:java`.
5. To launch the client, run `cd ../HW5Client && mvn exec:java`.
6. Verify the chat flow by logging in with two different usernames and confirming online list updates plus global/private chat routing.

## HW5 Submission Notes

1. Preferred archive for Blackboard: `HW5_submission_hw5-pure-clean.zip`.
2. That archive excludes `target`, IDE metadata, `.DS_Store`, and Zone Identifier files.
3. Keep the wireframe image folder out of the submission zip unless your instructor explicitly requests it.
