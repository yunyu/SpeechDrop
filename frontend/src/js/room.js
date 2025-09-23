import '../scss/base.scss';
import '../scss/room.scss';

import { createApp } from 'vue';

import RoomContainer from './RoomContainer.vue';

const roomConfig = window.roomConfig || {};

const htmlDecode = (input) => {
    let doc = new DOMParser().parseFromString(input, "text/html");
    return doc.documentElement.textContent;
}

const roomName = roomConfig.roomName;
if (roomName) {
    const decodedRoomName = htmlDecode(roomName);
    document.title = `${decodedRoomName} | SpeechDrop`;
}

createApp(RoomContainer).mount('#room-container');
