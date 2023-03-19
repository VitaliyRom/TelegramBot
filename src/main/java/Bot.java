import commands.AppBotCommand;
import commands.BotCommonCommands;
import functions.FilterOperation;
import functions.ImageOperation;
import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.GetFile;
import org.telegram.telegrambots.meta.api.methods.send.SendMediaGroup;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.methods.send.SendPhoto;
import org.telegram.telegrambots.meta.api.objects.*;
import org.telegram.telegrambots.meta.api.objects.File;
import org.telegram.telegrambots.meta.api.objects.media.InputMedia;
import org.telegram.telegrambots.meta.api.objects.media.InputMediaPhoto;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardButton;
import org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;
import utils.ImageUtils;
import utils.PhotoMessageUtils;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;


public class Bot extends TelegramLongPollingBot {

    HashMap<String, Message> messages = new HashMap<>();
    @Override
    public String getBotUsername() {
        return "javareagihh35ubot";
    }

    @Override
    public String getBotToken() {
        return "5953888349:AAG1Q5ep5QFGUmc2nSULKZc-3KoSSXwNiFM";
    }

    @Override
    public void onUpdateReceived(Update update) {
        Message message = update.getMessage();
        try {
            SendMessage responseTextMessage = runCommonCommand(message);
            if (responseTextMessage != null) {
                execute(responseTextMessage);
                return;

            }
            responseTextMessage = runPhotoMessage(message);
            if(responseTextMessage != null) {
                execute(responseTextMessage);
                return;
            }
        } catch (InvocationTargetException | IllegalAccessException | TelegramApiException e) {
            throw new RuntimeException(e);
        }

      try {
        Object responseMediaMessage = runPhotoFilter(message);
            if (responseMediaMessage != null) {
                if(responseMediaMessage instanceof SendMediaGroup) {
                    execute((SendMediaGroup) responseMediaMessage);
                } else if(responseMediaMessage instanceof SendMessage) {
                    execute((SendMessage) responseMediaMessage);
                }
            }
            return;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private List<File> getFilesByMessage(Message message) {
        List<PhotoSize> photoSizes = message.getPhoto();
        ArrayList<File> files = new ArrayList<>();
        if(photoSizes == null) return new ArrayList<>();
        for (PhotoSize photoSize : photoSizes) {
            final String fileId = photoSize.getFileId();
            try {
                files.add(sendApiMethod(new GetFile(fileId)));
            } catch (TelegramApiException e) {
                throw new RuntimeException(e);
            }
        }
        return files;
    }

    private SendMessage runCommonCommand(Message message) throws
            InvocationTargetException, IllegalAccessException {
        String text = message.getText();
        BotCommonCommands commands = new BotCommonCommands();
        Method[] classMethods = commands.getClass().getDeclaredMethods();
        for (Method method : classMethods) {
            if (method.isAnnotationPresent(AppBotCommand.class)) {
                AppBotCommand command = method.getAnnotation(AppBotCommand.class);
                if (command.name().equals(text)) {
                    method.setAccessible(true);
                    String responseText = (String) method.invoke(commands);
                    SendMessage sendMessage = new SendMessage(message.getChatId().toString(), responseText);
                    return sendMessage;
                }
            }
        }
        return null;
    }

    private SendMessage runPhotoMessage(Message message) {
        List<File> files = getFilesByMessage(message);
        if(files.isEmpty()) {
            return null;
        }
        String chatId = message.getChatId().toString();
        messages.put(chatId, message);
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        ArrayList<KeyboardRow> allKeyboardRows = new ArrayList<>(getKeyboardRows(FilterOperation.class));
        replyKeyboardMarkup.setKeyboard(allKeyboardRows);
        replyKeyboardMarkup.setOneTimeKeyboard(true);
        SendMessage sendMessage = new SendMessage();
        sendMessage.setChatId(chatId);
        sendMessage.setText("Choose the filter");
        sendMessage.setReplyMarkup(replyKeyboardMarkup);
        return sendMessage;
    }

    private Object runPhotoFilter(Message message) throws Exception {
        String text = message.getText();
        ImageOperation operation = ImageUtils.getOperation(text);
        if(operation == null) return null;
        String chatId = message.getChatId().toString();
        Message photoMessage = messages.get(chatId);
        if(photoMessage != null) {
            List<File> files = getFilesByMessage(photoMessage);
            List<String> paths = PhotoMessageUtils.savePhoto(files, getBotToken());
            return preparePhotoMessage(paths, operation, chatId);
        } else {
            SendMessage sendMessage = new SendMessage(chatId, "Mistake. You must send photo");
            return sendMessage;
        }
    }


    private SendMediaGroup preparePhotoMessage(List<String> localPaths, ImageOperation operation, String
            chatId) throws Exception {
        SendMediaGroup mediaGroup = new SendMediaGroup();
        ArrayList<InputMedia> medias = new ArrayList<>();
        for (String path : localPaths) {
            InputMedia inputMedia = new InputMediaPhoto();
            PhotoMessageUtils.processingImage(path, operation);
            inputMedia.setMedia(new java.io.File(path), "path");
            medias.add(inputMedia);
        }
        mediaGroup.setMedias(medias);
        mediaGroup.setChatId(chatId);

        return mediaGroup;

    }

    private ReplyKeyboardMarkup getKeyboard() {
        ReplyKeyboardMarkup replyKeyboardMarkup = new ReplyKeyboardMarkup();
        ArrayList<KeyboardRow> allKeyboardRows = new ArrayList<>();
        allKeyboardRows.addAll(getKeyboardRows(BotCommonCommands.class));
        allKeyboardRows.addAll(getKeyboardRows(FilterOperation.class));
        replyKeyboardMarkup.setKeyboard(allKeyboardRows);
        replyKeyboardMarkup.setOneTimeKeyboard(true);
        return replyKeyboardMarkup;
    }

    private ArrayList<KeyboardRow> getKeyboardRows(Class someClass) {
        Method[] methods = someClass.getDeclaredMethods();
        ArrayList<KeyboardRow> rows = new ArrayList<>();
        ArrayList<AppBotCommand> commands = new ArrayList<>();
        for (Method method : methods) {
            if (method.isAnnotationPresent(AppBotCommand.class)) {
                AppBotCommand command = method.getAnnotation(AppBotCommand.class);
                commands.add(command);
            }
        }
        int columnCount = 3;
        int rowsCount = (commands.size() / columnCount) + ((commands.size() % columnCount == 0) ? 0 : 1);
        for (int rowIndex = 0; rowIndex < rowsCount; rowIndex++) {
            KeyboardRow row = new KeyboardRow();
            for (int columnIndex = 0; columnIndex < columnCount; columnIndex++) {
                int index = rowIndex * columnCount + columnIndex;
                if ((index) >= commands.size()) continue;
                AppBotCommand command = commands.get(index);
                KeyboardButton button = new KeyboardButton(command.name());
                row.add(button);

            }
            rows.add(row);
        }
        return rows;
    }
}
