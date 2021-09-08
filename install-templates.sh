#!/bin/bash
# Installs Workflow file templates for IntelliJ and Android Studio.


OS="$(uname -s)"
echo "Installing Workflow file templates on $OS system..."
ideaConfigPath=""
if [[ "$OS" == Linux ]]; then
  ideaConfigPath="$HOME/.config"
elif [[ "$OS" == Darwin ]]; then
  ideaConfigPath="$HOME/Library/Application Support"
fi
TEMPLATES="$( cd "$( dirname "${BASH_SOURCE[0]}" )" && pwd )/fileTemplates"
for i in "$ideaConfigPath"/Google/AndroidStudio* \
         "$ideaConfigPath"/JetBrains/IdeaIC* \
         "$ideaConfigPath"/JetBrains/IntelliJIdea*
do
  echo $i
  if [[ -d "$i" ]]; then
    mkdir -p "$i/fileTemplates"
    cp -frv "$TEMPLATES"/* "$i/fileTemplates"
  fi
done

echo "Done."
echo ""
echo "Restart IntelliJ and/or AndroidStudio."
