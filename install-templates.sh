#!/bin/bash
# Installs Workflow file templates for IntelliJ and Android Studio.


OS="$(uname -s)"
echo "Installing Workflow file templates on $OS system..."
ideaConfigPath=""
if [[ "$OS" == Linux ]]; then
  ideaConfigPath='.config'
elif [[ "$OS" == Darwin ]]; then
  ideaConfigPath="Library/Application\ Support"
fi
TEMPLATES="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )/fileTemplates"

for i in $HOME/$ideaConfigPath/Google/AndroidStudio* \
         $HOME/$ideaConfigPath/JetBrains/IdeaIC*
do
  if [[ -d "$i" ]]; then
    mkdir -p "$i/fileTemplates"
    cp -frv "$TEMPLATES"/* "$i/fileTemplates"
  fi
done

echo "Done."
echo ""
echo "Restart IntelliJ and/or AndroidStudio."
