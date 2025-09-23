import '../scss/base.scss';
import '../scss/room.scss';

import { createApp } from 'vue';

import RoomContainer from './RoomContainer.vue';

const roomConfig = window.roomConfig || {};

if (typeof document !== 'undefined') {
    const roomName = roomConfig.roomName;
    if (roomName) {
        const textarea = document.createElement('textarea');
        textarea.innerHTML = roomName;
        const decodedRoomName = textarea.value;
        document.title = `${decodedRoomName} | SpeechDrop`;
    }
}

createApp(RoomContainer).mount('#room-container');
