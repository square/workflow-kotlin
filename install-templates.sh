#!/bin/bash
# Installs Workflow file templates for IntelliJ and Android Studio.

echo "Installing Workflow file templates..."

TEMPLATES="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )/fileTemplates"

for i in $HOME/Library/Application\ Support/Google/AndroidStudio* \
         $HOME/Library/Application\ Support/JetBrains/IdeaIC*
do
  if [[ -d "$i" ]]; then
    mkdir -p "$i/fileTemplates"
    cp -frv "$TEMPLATES"/* "$i/fileTemplates"
  fi
done

echo "Done."
echo ""
echo "Restart IntelliJ and/or AndroidStudio."